package com.otilm.core.service.handler.discovery;

import com.otilm.api.model.common.attribute.common.AttributeContent;
import com.otilm.api.model.common.attribute.common.MetadataAttribute;
import com.otilm.api.model.connector.discovery.DiscoveryProviderCertificateDataDto;
import com.otilm.api.model.connector.discovery.v2.DiscoveredCertificateDto;
import com.otilm.api.model.connector.discovery.v2.DiscoveredItemDto;
import com.otilm.api.model.connector.discovery.v2.DiscoveredKeyDto;
import com.otilm.api.model.connector.discovery.v2.DiscoveryEvent;
import com.otilm.api.model.connector.discovery.v2.DiscoveryProgressDto;
import com.otilm.api.model.connector.discovery.v2.DiscoveryResultsResponseDto;
import com.otilm.api.model.connector.discovery.v2.event.DiscoveryErrorEvent;
import com.otilm.api.model.connector.discovery.v2.event.DiscoveryProgressEvent;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.repository.CryptographicKeyItemRepository;
import com.otilm.core.dao.repository.DiscoveryCertificateRepository;
import com.otilm.core.dao.repository.DiscoveryRepository;
import com.otilm.core.model.discovery.DiscoveryRunLifecycle;
import com.otilm.core.model.discovery.DiscoveryWorkType;
import com.otilm.core.service.handler.CertificateHandler;
import com.otilm.core.service.writer.discovery.DiscoveryItemWriter;
import com.otilm.core.service.writer.discovery.DiscoveryWorkWriter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single funnel through which connector-reported discovery data enters Core.
 *
 * <p>
 * <b>Authoritative versus advisory.</b> Only a drain page carries items, and only a drain page moves the ingestion
 * cursor. Pushed events are advisory: they refresh cosmetic progress, record a connector-side complaint, or ask for a
 * tick — none of them commits run state, and none of them touches {@code last_applied_sequence}. State transitions are
 * committed exclusively by the tick workers from an authoritative connector response.
 *
 * <p>
 * {@code applyAdvisoryEvent} has no production caller yet — it is groundwork, not a live path.
 *
 * <p>
 * <b>Why the run row is locked.</b> A drain page's staged rows and the cursor advance that accounts for them must be
 * one atomic step, and the reaper takes the same lock before it declares a run's work lost. Holding it here means a
 * drain in flight can never be reaped out from under itself.
 */
@Service
public class DiscoveryEventIngestor {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryEventIngestor.class);

    private final DiscoveryRepository discoveryRepository;
    private final DiscoveryItemWriter itemWriter;
    private final DiscoveryWorkWriter workWriter;
    private final CertificateHandler certificateHandler;
    private final CryptographicKeyItemRepository keyItemRepository;
    private final DiscoveryCertificateRepository certificateRepository;

    public DiscoveryEventIngestor(DiscoveryRepository discoveryRepository, DiscoveryItemWriter itemWriter,
            DiscoveryWorkWriter workWriter, CertificateHandler certificateHandler,
            CryptographicKeyItemRepository keyItemRepository, DiscoveryCertificateRepository certificateRepository) {
        this.discoveryRepository = discoveryRepository;
        this.itemWriter = itemWriter;
        this.workWriter = workWriter;
        this.certificateHandler = certificateHandler;
        this.keyItemRepository = keyItemRepository;
        this.certificateRepository = certificateRepository;
    }

    /**
     * Stages a drained page and advances the run's ingestion cursor.
     *
     * <p>
     * <b>The cursor advances by the highest item sequence this page actually carried</b> — never by the response's
     * {@code highestSequence}, which is run-wide and counts items the connector has produced but not yet handed over.
     * Advancing by the run-wide value would skip every item between the page's last one and it. An empty page moves
     * nothing.
     *
     * <p>
     * <b>Idempotency</b> comes from two layers. The cursor handles redelivery: an item at or below it is dropped before
     * staging, so a repeated page is a no-op and an overlapping one stages only its new tail. The {@code uniqueRef}
     * handles everything the cursor cannot — a repeat inside one page, and a re-send under a newer sequence, which the
     * contract permits and the cursor lets through. Both tables carry a unique index over it per run.
     *
     * @return whether the cursor advanced. A page that carried nothing new is how a connector loops, so the caller
     * needs to tell "more is coming" from "the same page again".
     */
    @Transactional
    public boolean applyDrainPage(UUID discoveryUuid, DiscoveryResultsResponseDto page) {
        Optional<Discovery> located = lockRun(discoveryUuid, "drain page");
        if (located.isEmpty()) {
            return false;
        }
        Discovery run = located.get();
        if (DiscoveryRunLifecycle.isTerminal(run.getStatus())) {
            // Terminal only, where every other guard in the engine uses hasLeftTheConnector. PROCESSING is
            // deliberately still open to staging: a page in flight across the handover carries items the run has
            // not imported yet, and the process worker picks them up on its next tick. What makes that safe is
            // the run row lock held here -- the worker re-reads the backlog under the same lock before it ends
            // the run, so a page either lands in time to be imported or finds the run already terminal here.
            logger.warn("Dropping drain page for discovery {}: the run ended as {}", discoveryUuid, run.getStatus());
            return false;
        }
        List<DiscoveredItemDto> items = page.getItems() == null ? List.of() : page.getItems();
        if (items.isEmpty()) {
            return false;
        }
        recordMalformed(run, items);

        long cursor = run.getLastAppliedSequence();
        List<DiscoveredItemDto> fresh = items.stream().filter(item -> sequenceOf(item) > cursor).toList();
        stage(run, fresh);

        long highestReceived = items.stream().mapToLong(DiscoveryEventIngestor::sequenceOf).max().orElse(cursor);
        if (highestReceived > cursor) {
            run.setLastAppliedSequence(highestReceived);
        }
        logger
                .debug("Staged {} of {} drained items for discovery {}; cursor {} -> {}", fresh.size(), items.size(),
                        discoveryUuid, cursor, run.getLastAppliedSequence());
        return run.getLastAppliedSequence() > cursor;
    }

    /**
     * Applies one pushed event. Progress is cosmetic, an error joins the run's message log, and the two events that
     * mean "there is something to fetch" schedule the tick that fetches it authoritatively — a pushed
     * {@code RESULT_BATCH}'s own items are ignored on purpose, since results enter Core only through a call Core made.
     *
     * <p>
     * Nothing is scheduled for a run that has already finished: its agenda was deleted by the terminal transition, and
     * re-creating a row there would resurrect work for a run no connector still tracks.
     */
    @Transactional
    public void applyAdvisoryEvent(UUID discoveryUuid, DiscoveryEvent event) {
        Optional<Discovery> located = lockRun(discoveryUuid, "advisory event");
        if (located.isEmpty()) {
            return;
        }
        Discovery run = located.get();
        if (DiscoveryRunLifecycle.isTerminal(run.getStatus())) {
            // Every branch of this switch writes to the run, not just the two that schedule work. A late progress
            // snapshot would overwrite the run's final one, and a late error would append below the terminal
            // reason, so a finished run would no longer read as ending with its ending.
            logger
                    .debug("Ignoring {} event for discovery {}: the run ended as {}", event.getType(), discoveryUuid,
                            run.getStatus());
            return;
        }
        switch (event.getType()) {
            case PROGRESS -> run.setProgress(snapshotOf((DiscoveryProgressEvent) event));
            case ERROR -> {
                DiscoveryErrorEvent error = (DiscoveryErrorEvent) event;
                // The code only. The message beside it is connector-authored prose, and run_messages is read
                // through the API -- the same reason DiscoveryConnectorErrors.describe forwards nothing a
                // connector wrote. The full text goes to the log instead.
                logger.warn("Discovery {} connector error {}: {}", discoveryUuid, error.getCode(), error.getMessage());
                run
                        .setRunMessages(DiscoveryRunLifecycle
                                .append(run.getRunMessages(), "Connector reported %s".formatted(error.getCode())));
            }
            case STATE_CHANGED -> scheduleNow(run, DiscoveryWorkType.STATUS);
            case RESULT_BATCH -> scheduleNow(run, DiscoveryWorkType.DRAIN);
            case HEARTBEAT -> logger.trace("Heartbeat for discovery {}", discoveryUuid);
        }
    }

    /**
     * Records, on the run, items a connector sent without the sequence the wire requires. They cannot be staged — a
     * sequence is what the cursor is made of — so without this line they would vanish leaving no row, no message and
     * nothing for the connector's author to notice.
     */
    private void recordMalformed(Discovery run, List<DiscoveredItemDto> items) {
        List<String> refs = items
                .stream()
                .filter(item -> item.getSequence() == null)
                .map(DiscoveredItemDto::getUniqueRef)
                .toList();
        if (refs.isEmpty()) {
            return;
        }
        logger.warn("Discovery {} received {} item(s) with no sequence: {}", run.getUuid(), refs.size(), refs);
        run
                .setRunMessages(DiscoveryRunLifecycle
                        .append(run.getRunMessages(),
                                "%d item(s) arrived without a sequence and were skipped".formatted(refs.size())));
    }

    /**
     * Registers the page's metadata definitions before any of its rows reach the import pipeline.
     *
     * <p>
     * The pipeline imports content groups in parallel, and each group applies its rows' metadata. Two groups carrying
     * the same new definition would then race to insert it, and the loser's whole group rolls back and is recorded as
     * failed — a certificate reported as unimportable for no reason but timing. Registering once up front removes the
     * race, which is why the v1 download path does the same thing before submitting its batch.
     *
     * <p>
     * The registration commits in its own transaction, so a page that later rolls back does not withdraw a definition
     * another page may already be relying on.
     */
    private void registerMetadataDefinitions(Discovery run, List<DiscoveredItemDto> items) {
        List<MetadataAttribute> definitions = new ArrayList<>();
        Map<String, Set<AttributeContent>> contentsByDefinition = new HashMap<>();
        for (DiscoveredItemDto item : items) {
            if (item.getMeta() == null) {
                continue;
            }
            for (MetadataAttribute attribute : item.getMeta()) {
                Set<AttributeContent> contents = contentsByDefinition.get(attribute.getUuid());
                if (contents == null) {
                    definitions.add(attribute);
                    contents = new HashSet<>();
                    contentsByDefinition.put(attribute.getUuid(), contents);
                }
                contents.addAll(attribute.getContent());
            }
        }
        if (definitions.isEmpty()) {
            return;
        }
        certificateHandler
                .updateMetadataDefinition(definitions, contentsByDefinition, run.getConnectorUuid(),
                        run.getConnectorName());
    }

    private void stage(Discovery run, List<DiscoveredItemDto> items) {
        List<DiscoveryProviderCertificateDataDto> certificates = new ArrayList<>();
        Set<String> stagedCertificateRefs = alreadyStagedRefs(run, items);
        List<String> malformedPayloads = new ArrayList<>();
        for (DiscoveredItemDto item : items) {
            if (item.getResource() != Resource.CERTIFICATE) {
                itemWriter.stage(run.getUuid(), item, isNewlyDiscovered(item));
            } else if (stagedCertificateRefs.add(item.getUniqueRef())) {
                DiscoveryProviderCertificateDataDto data = asCertificateData(item);
                if (data == null) {
                    malformedPayloads.add(item.getUniqueRef());
                } else {
                    certificates.add(data);
                }
            }
        }
        registerMetadataDefinitions(run, items);
        List<String> problems = new ArrayList<>();
        malformedPayloads
                .forEach(ref -> problems
                        .add("Certificate %s could not be staged: its payload was not a certificate".formatted(ref)));
        if (!certificates.isEmpty()) {
            // Certificates keep the v1 staging table and its write, so discovery_certificate stays the single
            // certificate store until the evidence-gated unification (core#2027).
            problems.addAll(certificateHandler.stageDiscoveredCertificates(batchLabel(items), run, certificates, true));
        }
        if (!problems.isEmpty()) {
            run.setRunMessages(DiscoveryRunLifecycle.append(run.getRunMessages(), problems));
        }
    }

    /**
     * The certificate references from this page that the run has already staged, as the starting state of the page's
     * own dedupe set — so one set answers both "twice in this page" and "already staged by an earlier drain".
     *
     * <p>
     * The contract makes {@code uniqueRef} the key Core dedupes an item by <b>across drains and retries</b>, so a
     * connector may re-send an item under a newer sequence, where the cursor filter no longer catches it. Only the
     * page's own references are looked up rather than the run's whole set, which on a large run is every certificate it
     * has found. Every drain for a run is serialised by the row lock this method runs under, so reading and filtering
     * is enough; the table's partial unique index is the backstop, not the mechanism.
     */
    private Set<String> alreadyStagedRefs(Discovery run, List<DiscoveredItemDto> items) {
        Set<String> refs = items
                .stream()
                .filter(item -> item.getResource() == Resource.CERTIFICATE)
                .map(DiscoveredItemDto::getUniqueRef)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (refs.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(certificateRepository.findStagedRefs(run.getUuid(), refs));
    }

    /** Names the page by what it carried, not by where the cursor stood before it. */
    private static String batchLabel(List<DiscoveredItemDto> items) {
        return "drain@" + items.stream().mapToLong(DiscoveryEventIngestor::sequenceOf).max().orElse(0L);
    }

    /**
     * @return the v1 staging shape, or {@code null} when the payload does not match the resource it declared — one
     * malformed item costs itself rather than throwing and rolling back the whole page, which would leave the cursor
     * unadvanced and the same poison page redelivered forever
     */
    private static DiscoveryProviderCertificateDataDto asCertificateData(DiscoveredItemDto item) {
        if (!(item.getPayload() instanceof DiscoveredCertificateDto certificate)) {
            return null;
        }
        DiscoveryProviderCertificateDataDto data = new DiscoveryProviderCertificateDataDto();
        // The connector's own dedupe key, so a staging failure is logged under the reference the connector
        // knows the item by.
        data.setUuid(item.getUniqueRef());
        data.setBase64Content(certificate.getCertificateData());
        data.setMeta(item.getMeta() == null ? List.of() : item.getMeta());
        return data;
    }

    /**
     * Whether the item was absent from inventory when staged — the flag the run detail filters on. Keys correlate on
     * their intrinsic fingerprint; anything else has no inventory to be absent from yet, so it counts as new.
     */
    private boolean isNewlyDiscovered(DiscoveredItemDto item) {
        if (!(item.getPayload() instanceof DiscoveredKeyDto key) || key.getFingerprint() == null
                || key.getFingerprint().isBlank()) {
            return true;
        }
        return keyItemRepository.findByFingerprint(key.getFingerprint()).isEmpty();
    }

    private void scheduleNow(Discovery run, DiscoveryWorkType workType) {
        if (DiscoveryRunLifecycle.hasLeftTheConnector(run.getStatus())) {
            // Covers PROCESSING as well as the terminal states: the connector released the run at the swap,
            // so re-arming a STATUS or DRAIN row here would drive a tick against a handle that no longer exists.
            logger
                    .debug("Ignoring advisory event asking for a {} tick on discovery {}, already {}", workType,
                            run.getUuid(), run.getStatus());
            return;
        }
        // Expedited, not armed: a pushed event is not an answer from the connector, so it may bring the tick
        // forward but must not refresh a failure budget no successful call has earned.
        workWriter.expedite(run.getUuid(), workType, OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * Copied field by field rather than stored as-is: the event is a {@code DiscoveryProgressDto} subclass carrying the
     * stream's discriminator, and the column holds the plain snapshot the status poll writes too.
     */
    private static DiscoveryProgressDto snapshotOf(DiscoveryProgressEvent event) {
        DiscoveryProgressDto snapshot = new DiscoveryProgressDto();
        snapshot.setProcessed(event.getProcessed());
        snapshot.setTotalEstimate(event.getTotalEstimate());
        snapshot.setPhase(event.getPhase());
        snapshot.setByResource(event.getByResource());
        return snapshot;
    }

    private Optional<Discovery> lockRun(UUID discoveryUuid, String what) {
        Optional<Discovery> run = discoveryRepository.findWithLockByUuid(discoveryUuid);
        if (run.isEmpty()) {
            // The run was deleted while its work was in flight; the agenda rows went with it by cascade, so
            // this is a redelivery of an obsolete tick rather than a fault.
            logger.warn("Dropping {} for discovery {}: the run no longer exists", what, discoveryUuid);
        }
        return run;
    }

    private static long sequenceOf(DiscoveredItemDto item) {
        // Wire-required and validated, so a null here is a non-conformant connector: treat it as below any
        // cursor rather than failing the whole page on one malformed item.
        return item.getSequence() == null ? 0L : item.getSequence();
    }
}
