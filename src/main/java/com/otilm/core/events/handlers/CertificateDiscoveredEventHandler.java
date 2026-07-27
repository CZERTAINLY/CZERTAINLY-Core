package com.otilm.core.events.handlers;

import com.otilm.api.exception.EventException;
import com.otilm.api.exception.RuleException;
import com.otilm.api.model.common.events.data.CertificateDiscoveredEventData;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.api.model.core.other.ResourceEvent;
import com.otilm.api.model.core.workflows.EventStatus;
import com.otilm.core.dao.entity.Certificate;
import com.otilm.core.dao.entity.DiscoveryCertificate;
import com.otilm.core.dao.entity.DiscoveryHistory;
import com.otilm.core.dao.entity.workflows.EventHistory;
import com.otilm.core.dao.entity.workflows.Trigger;
import com.otilm.core.dao.entity.workflows.TriggerAssociation;
import com.otilm.core.dao.entity.workflows.TriggerHistory;
import com.otilm.core.dao.repository.CertificateRepository;
import com.otilm.core.dao.repository.DiscoveryCertificateRepository;
import com.otilm.core.dao.repository.DiscoveryRepository;
import com.otilm.core.evaluator.CertificateTriggerEvaluator;
import com.otilm.core.events.EventContext;
import com.otilm.core.events.EventContextTriggers;
import com.otilm.core.events.EventHandler;
import com.otilm.core.events.data.DiscoveryResult;
import com.otilm.core.events.handlers.discovery.*;
import com.otilm.core.events.transaction.TransactionHandler;
import com.otilm.core.model.auth.ResourceAction;
import com.otilm.core.security.authz.AuthorizationEnforcer;
import com.otilm.core.service.writer.DiscoveryWriter;
import com.otilm.core.messaging.jms.producers.ValidationProducer;
import com.otilm.core.messaging.model.EventMessage;
import com.otilm.core.messaging.model.ValidationMessage;
import com.otilm.core.service.CertificateInternalService;
import com.otilm.core.service.handler.CertificateHandler;
import com.otilm.core.tasks.ScheduledJobInfo;
import com.otilm.core.util.CertificateUtil;
import com.pivovarit.collectors.ParallelCollectors;
import org.bouncycastle.asn1.x509.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Transactional
@Component(ResourceEvent.Codes.CERTIFICATE_DISCOVERED)
public class CertificateDiscoveredEventHandler extends EventHandler<Certificate> {

    private static final Integer MAXIMUM_PARALLELISM = 5;
    private static final Logger logger = LoggerFactory.getLogger(CertificateDiscoveredEventHandler.class);

    private static final Semaphore processCertSemaphore = new Semaphore(10);

    private CertificateHandler certificateHandler;
    private TransactionHandler transactionHandler;
    private ValidationProducer validationProducer;

    private CertificateInternalService certificateService;
    private DiscoveryRepository discoveryRepository;
    private DiscoveryCertificateRepository discoveryCertificateRepository;
    private DiscoveryWriter discoveryWriter;
    private AuthorizationEnforcer authorizationEnforcer;

    @Autowired
    protected CertificateDiscoveredEventHandler(CertificateRepository repository, CertificateTriggerEvaluator ruleEvaluator) {
        super(repository, ruleEvaluator);
    }

    @Autowired
    public void setDiscoveryCertificateHandler(CertificateHandler certificateHandler) {
        this.certificateHandler = certificateHandler;
    }

    @Autowired
    public void setTransactionHandler(TransactionHandler transactionHandler) {
        this.transactionHandler = transactionHandler;
    }

    @Autowired
    public void setValidationProducer(ValidationProducer validationProducer) {
        this.validationProducer = validationProducer;
    }

    @Autowired
    public void setCertificateService(CertificateInternalService certificateService) {
        this.certificateService = certificateService;
    }

    @Autowired
    public void setDiscoveryRepository(DiscoveryRepository discoveryRepository) {
        this.discoveryRepository = discoveryRepository;
    }

    @Autowired
    public void setDiscoveryCertificateRepository(DiscoveryCertificateRepository discoveryCertificateRepository) {
        this.discoveryCertificateRepository = discoveryCertificateRepository;
    }

    @Autowired
    public void setDiscoveryWriter(DiscoveryWriter discoveryWriter) {
        this.discoveryWriter = discoveryWriter;
    }

    @Autowired
    public void setAuthorizationEnforcer(AuthorizationEnforcer authorizationEnforcer) {
        this.authorizationEnforcer = authorizationEnforcer;
    }

    @Override
    protected EventContext<Certificate> prepareContext(EventMessage eventMessage) throws EventException {
        EventContext<Certificate> context = new EventContext<>(eventMessage, triggerEvaluator, null, null);
        fetchEventTriggers(context, null, null); // triggers without resource and its UUID are platform ones

        return context;
    }

    @Override
    protected Object getEventData(Certificate certificate, Object eventMessageData) {
        CertificateDiscoveredEventData eventData = new CertificateDiscoveredEventData();
        eventData.setCertificateUuid(certificate.getUuid());
        eventData.setFingerprint(certificate.getFingerprint());
        eventData.setSerialNumber(certificate.getSerialNumber());
        eventData.setSubjectDn(certificate.getSubjectDn());
        eventData.setIssuerDn(certificate.getIssuerDn());
        eventData.setNotBefore(certificate.getNotBefore().toInstant().atZone(ZoneId.systemDefault()));
        eventData.setExpiresAt(certificate.getNotAfter().toInstant().atZone(ZoneId.systemDefault()));

        return eventData;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void handleEvent(EventMessage eventMessage) throws EventException {
        if (eventMessage.getOverrideResource() == null || eventMessage.getOverrideObjectUuid() == null) {
            throw new EventException(eventMessage.getEvent(), "Event currently supported only through discovery as overriding resource");
        }

        // merge platform and discovery triggers to process them together, first discovery one and then platform
        EventContext<Certificate> context = prepareContext(eventMessage);
        EventContextTriggers discoveryTriggers = fetchEventTriggers(context, eventMessage.getOverrideResource(), eventMessage.getOverrideObjectUuid());
        List<TriggerAssociation> mergedTriggers = new ArrayList<>(discoveryTriggers.getTriggers());
        List<TriggerAssociation> mergedIgnoreTriggers = new ArrayList<>(discoveryTriggers.getIgnoreTriggers());
        mergedTriggers.addAll(context.getPlatformTriggers().getTriggers());
        mergedIgnoreTriggers.addAll(context.getPlatformTriggers().getIgnoreTriggers());

        // Get newly discovered certificates
        DiscoveryHistory discovery = discoveryRepository.findByUuid(eventMessage.getOverrideObjectUuid()).orElseThrow(() -> new EventException(eventMessage.getEvent(), "Discovery with UUID %s not found".formatted(eventMessage.getOverrideObjectUuid())));
        String originalMessage = discovery.getStatus() != DiscoveryStatus.IN_PROGRESS ? discovery.getMessage() : null;
        List<DiscoveryCertificate> discoveredCertificates = discoveryCertificateRepository.findByDiscoveryUuidAndNewlyDiscovered(eventMessage.getOverrideObjectUuid(), true, Pageable.unpaged());
        logger.debug("Going to process {} triggers on {} discovered certificates", mergedIgnoreTriggers.size() + mergedTriggers.size(), context.getResourceObjects().size());

        // The DISCOVERY_FINISHED event is the only signal that rolls the top-level status out of PROCESSING, so it
        // must be emitted even when post-processing fails partway; otherwise the discovery is stranded in PROCESSING
        // with a growing duration and can never be finalized.
        boolean discoveryFinishEmitted = false;
        try {
            handleDiscoveredCertificates(context, discovery, originalMessage, discoveredCertificates, mergedIgnoreTriggers, mergedTriggers);
            discoveryFinishEmitted = true;
        } catch (Exception e) {
            // Catch broadly on purpose: exceptions escaping handleEvent are only logged and dropped by the JMS
            // listener (no redelivery, no DLQ), so letting one propagate would strand the discovery in PROCESSING.
            // Finalizing as WARNING in the finally block is strictly better; the failure is still logged here.
            logger.error("Post-processing of discovered certificates for discovery {} did not complete: {}", discovery.getName(), e.getMessage(), e);
        } finally {
            if (!discoveryFinishEmitted) {
                emitDiscoveryFinished(discovery, context, DiscoveryStatus.WARNING,
                        "Discovery post-processing did not complete; some certificates may not have been processed.");
            }
        }
    }

    private void handleDiscoveredCertificates(EventContext<Certificate> context, DiscoveryHistory discovery, String originalMessage,
                                              List<DiscoveryCertificate> discoveredCertificates, List<TriggerAssociation> mergedIgnoreTriggers,
                                              List<TriggerAssociation> mergedTriggers) {
        if (discoveredCertificates.isEmpty()) {
            emitDiscoveryFinished(discovery, context, DiscoveryStatus.PROCESSING, originalMessage);
            return;
        }

        EventHistory eventHistoryDiscovery = createEventHistory(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, discovery.getUuid());
        EventHistory eventHistoryPlatform = createEventHistory(ResourceEvent.CERTIFICATE_DISCOVERED, null, null);

        // One resource-level check per run rather than one per certificate: enforcement is a blocking OPA request
        // and must not be held across a transaction.
        authorizationEnforcer.enforce(Resource.CERTIFICATE, ResourceAction.CREATE);

        // Group by content so two threads can no longer hold the same certificate and race on its insert.
        List<DiscoveryContentGroup> groups = discoveredCertificates.stream()
                .collect(Collectors.groupingBy(DiscoveryCertificate::getCertificateContentId,
                        LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new DiscoveryContentGroup(entry.getKey(), entry.getValue()))
                .toList();

        DiscoveryRunContext runContext = new DiscoveryRunContext(discovery.getUuid(), discovery.getName(),
                discovery.getConnectorUuid(), discovery.getConnectorName(), discovery.getKind(),
                context.getUserUuid(), mergedIgnoreTriggers, mergedTriggers,
                eventHistoryDiscovery.getUuid(), eventHistoryPlatform.getUuid(), groups.size(), context);

        DiscoveryRunAccumulator accumulator = new DiscoveryRunAccumulator();
        Map<PublicKey, List<UUID>> keyToCertificates = new LinkedHashMap<>();
        Map<PublicKey, List<UUID>> altKeyToCertificates = new LinkedHashMap<>();

        try (ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            DelegatingSecurityContextExecutor executor = new DelegatingSecurityContextExecutor(
                    virtualThreadExecutor, SecurityContextHolder.getContext());

            // parallelToStream, not parallel: results arrive on this thread in completion order, which is what
            // lets the steps below own the key maps and the progress counter without synchronisation.
            List<GroupImportResult> results = groups.stream()
                    .collect(ParallelCollectors.parallelToStream(
                            group -> importGroupSafely(runContext, group), executor, MAXIMUM_PARALLELISM))
                    .toList();

            int completed = 0;
            for (GroupImportResult result : results) {
                accumulator.accept(result);
                writeBookkeeping(accumulator, result);
                mergeKeyEntries(accumulator, result, keyToCertificates, altKeyToCertificates);
                completed++;
                if (completed % MAXIMUM_PARALLELISM == 0) {
                    reportProgress(runContext, completed);
                }
            }
        }

        associateKeys(accumulator, keyToCertificates, false);
        associateKeys(accumulator, altKeyToCertificates, true);

        saveEventHistory(eventHistoryDiscovery, EventStatus.FINISHED);
        saveEventHistory(eventHistoryPlatform, EventStatus.FINISHED);

        validationProducer.produceMessage(new ValidationMessage(Resource.CERTIFICATE, null,
                runContext.discoveryUuid(), runContext.discoveryName(), null, null));
        DiscoveryResult result = decideFinalStatus(accumulator.counts(), originalMessage);
        emitDiscoveryFinished(discovery, context, result.getDiscoveryStatus(), result.getMessage());
    }

    /**
     * A clean run reports PROCESSING, which the finish handler rolls up to COMPLETED. Any gap reports WARNING and
     * contributes its own sentence, so two simultaneous partial failures are both visible rather than the first
     * hiding the rest.
     */
    static DiscoveryResult decideFinalStatus(DiscoveryRunCounts counts, String originalMessage) {
        if (counts.allClear()) {
            return new DiscoveryResult(DiscoveryStatus.PROCESSING, originalMessage);
        }
        List<String> sentences = new ArrayList<>();
        if (counts.inventoryGaps() > 0) {
            sentences.add("%d certificate(s) could not be imported into the inventory."
                    .formatted(counts.inventoryGaps()));
        }
        if (counts.keyGaps() > 0) {
            sentences.add("%d certificate(s) were imported without a public key association."
                    .formatted(counts.keyGaps()));
        }
        if (counts.notAttempted() > 0) {
            sentences.add("%d certificate(s) could not be processed to a result."
                    .formatted(counts.notAttempted()));
        }
        if (counts.bookkeepingFailures() > 0) {
            sentences.add("Some per-certificate detail could not be recorded.");
        }
        sentences.add("See the discovery certificate list for per-certificate detail.");
        return new DiscoveryResult(DiscoveryStatus.WARNING, String.join(" ", sentences));
    }

    /**
     * Never throws and never returns null. A result that does not arrive is indistinguishable from a group that was
     * never attempted, and consuming the stream would then either dereference null or silently drop the rollback
     * outcomes this design exists to record.
     */
    private GroupImportResult importGroupSafely(DiscoveryRunContext context, DiscoveryContentGroup group) {
        List<UUID> rowUuids = group.rows().stream().map(DiscoveryCertificate::getUuid).toList();
        try {
            processCertSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new GroupImportResult(group.certificateContentId(),
                    resultsFor(rowUuids, DiscoveryCertificateOutcome.NOT_ATTEMPTED,
                            "the import was interrupted before it began"), List.of(), false);
        }
        try {
            return transactionHandler.runInNewTransaction(() -> importContentGroup(context, group));
        } catch (Exception e) {
            logger.error("Unable to import discovered certificate content {}: {}",
                    group.certificateContentId(), e.getMessage(), e);
            return new GroupImportResult(group.certificateContentId(),
                    resultsFor(rowUuids, DiscoveryCertificateOutcome.IMPORT_ROLLED_BACK,
                            "Import rolled back: " + DiscoveryFailureReason.shape(e)), List.of(), false);
        } finally {
            processCertSemaphore.release();
        }
    }

    /**
     * Runs in its own transaction, per {@code DiscoveryWriter}'s contract: these writes record the outcome of the
     * import unit, so joining a transaction that is rolling back would discard them.
     */
    private void writeBookkeeping(DiscoveryRunAccumulator accumulator, GroupImportResult result) {
        for (DiscoveryCertificateResult row : result.rowResults()) {
            // Leaving processed = false is the truthful record for a row the platform never reached.
            if (row.outcome() == DiscoveryCertificateOutcome.NOT_ATTEMPTED) {
                continue;
            }
            try {
                transactionHandler.runInNewTransaction(() ->
                        discoveryWriter.markProcessed(row.discoveryCertificateUuid(), row.detail()));
            } catch (Exception e) {
                logger.error("Could not record the outcome of discovery certificate {}: {}",
                        row.discoveryCertificateUuid(), e.getMessage(), e);
                accumulator.recordBookkeepingFailure();
            }
        }
    }

    private static void mergeKeyEntries(DiscoveryRunAccumulator accumulator, GroupImportResult result,
                                        Map<PublicKey, List<UUID>> keyToCertificates,
                                        Map<PublicKey, List<UUID>> altKeyToCertificates) {
        if (!result.committed()) {
            return;
        }
        for (KeyQueueEntry entry : result.keyEntries()) {
            if (entry.isUnparseable()) {
                accumulator.failKeyAssociation(entry.certificateUuid(), "the %s key could not be decoded: %s"
                        .formatted(entry.alternative() ? "alternative" : "primary", entry.unparseableReason()));
                continue;
            }
            Map<PublicKey, List<UUID>> target = entry.alternative() ? altKeyToCertificates : keyToCertificates;
            target.computeIfAbsent(entry.publicKey(), key -> new ArrayList<>()).add(entry.certificateUuid());
        }
    }

    /**
     * Per-entry isolation is deliberate: one failing key must not abort the remaining uploads or the FINISHED
     * event-history bookkeeping.
     */
    private void associateKeys(DiscoveryRunAccumulator accumulator,
                               Map<PublicKey, List<UUID>> keysToCertificates, boolean alternative) {
        String label = alternative ? "alternative" : "primary";
        for (Map.Entry<PublicKey, List<UUID>> entry : keysToCertificates.entrySet()) {
            List<UUID> certificateUuids = entry.getValue();
            try {
                boolean associated = alternative
                        ? certificateHandler.uploadDiscoveredCertificateAltKey(entry.getKey(), certificateUuids)
                        : certificateHandler.uploadDiscoveredCertificateKey(entry.getKey(), certificateUuids);
                if (!associated) {
                    failAll(accumulator, certificateUuids,
                            "the %s key could not be associated with a committed certificate".formatted(label));
                }
            } catch (Exception e) {
                logger.error("Could not associate the {} public key of certificates {}: {}",
                        label, certificateUuids, e.getMessage(), e);
                failAll(accumulator, certificateUuids,
                        "the %s key upload failed: %s".formatted(label, DiscoveryFailureReason.shape(e)));
            }
        }
    }

    private static void failAll(DiscoveryRunAccumulator accumulator, List<UUID> certificateUuids, String reason) {
        certificateUuids.forEach(certificateUuid -> accumulator.failKeyAssociation(certificateUuid, reason));
    }

    private void reportProgress(DiscoveryRunContext context, int completedGroups) {
        int percentage = (int) ((completedGroups / (double) context.totalGroups()) * 100);
        discoveryWriter.updateProgressMessage(context.discoveryUuid(), String.format(
                "Processed %d %% of newly discovered certificates (%d / %d)",
                percentage, completedGroups, context.totalGroups()));
    }

    private static List<DiscoveryCertificateResult> resultsFor(List<UUID> rowUuids,
                                                               DiscoveryCertificateOutcome outcome, String detail) {
        return rowUuids.stream()
                .map(rowUuid -> new DiscoveryCertificateResult(rowUuid, outcome, detail))
                .toList();
    }

    private void emitDiscoveryFinished(DiscoveryHistory discovery, EventContext<Certificate> context, DiscoveryStatus status, String message) {
        eventProducer.produceMessage(DiscoveryFinishedEventHandler.constructEventMessage(
                discovery.getUuid(), context.getUserUuid(), context.getScheduledJobInfo(),
                new DiscoveryResult(status, message)));
    }

    /**
     * Imports the one certificate shared by a content group, applying every row's metadata.
     *
     * <p>Returns its outcomes and pending key associations rather than writing to shared state, so a transaction
     * that rolls back cannot leave a queued key behind pointing at a certificate that no longer exists.
     */
    GroupImportResult importContentGroup(DiscoveryRunContext context, DiscoveryContentGroup group) {
        try {
            return importContentGroupInternal(context, group);
        } catch (RuleException e) {
            // Trigger evaluation failed after the certificate committed. The import stands; trigger history
            // records the trigger-side failure, so this is not an inventory gap.
            logger.error("Trigger evaluation failed for discovered content {}: {}",
                    group.certificateContentId(), e.getMessage(), e);
            return new GroupImportResult(group.certificateContentId(),
                    resultsFor(group.rows().stream().map(DiscoveryCertificate::getUuid).toList(),
                            DiscoveryCertificateOutcome.IMPORTED, null), List.of(), true);
        }
    }

    private GroupImportResult importContentGroupInternal(DiscoveryRunContext context, DiscoveryContentGroup group)
            throws RuleException {
        List<UUID> rowUuids = group.rows().stream().map(DiscoveryCertificate::getUuid).toList();

        X509Certificate x509Cert;
        DiscoveredCertificateImport imported;
        try {
            x509Cert = CertificateUtil.parseCertificate(group.rows().getFirst().getCertificateContent().getContent());
            imported = certificateService.createDiscoveredCertificateAtomic(x509Cert);
        } catch (Exception e) {
            logger.error("Unable to create certificate for discovered content {}: {}",
                    group.certificateContentId(), e.getMessage(), e);
            return new GroupImportResult(group.certificateContentId(),
                    resultsFor(rowUuids, DiscoveryCertificateOutcome.ENTITY_CREATION_FAILED,
                            "Unable to create certificate entity: " + DiscoveryFailureReason.shape(e)),
                    List.of(), false);
        }

        // Always the surviving row: on a lost insert race it carries the winner's UUID, so trigger history and
        // key entries below must be derived from it rather than from the entity this caller built.
        Certificate certificate = imported.certificate();

        EventHistory discoveryEventHistory = eventHistoryRepository.getReferenceById(context.discoveryEventHistoryUuid());
        EventHistory platformEventHistory = eventHistoryRepository.getReferenceById(context.platformEventHistoryUuid());
        UUID referenceRowUuid = rowUuids.getFirst();

        List<TriggerHistory> ignoreHistories = new ArrayList<>();
        for (TriggerAssociation triggerAssociation : context.ignoreTriggers()) {
            EventHistory eventHistory = triggerAssociation.getResource() == null ? platformEventHistory : discoveryEventHistory;
            TriggerHistory triggerHistory = context.eventContext().getTriggerEvaluator().evaluateTrigger(
                    triggerAssociation.getTrigger(), triggerAssociation, certificate, referenceRowUuid, null, eventHistory);
            ignoreHistories.add(triggerHistory);
            if (triggerHistory.isActionsPerformed()) {
                return new GroupImportResult(group.certificateContentId(),
                        resultsFor(rowUuids, DiscoveryCertificateOutcome.IGNORED, null), List.of(), true);
            }
        }
        ignoreHistories.forEach(history -> history.setObjectUuid(certificate.getUuid()));

        CertificateDiscoveredEventData eventData = (CertificateDiscoveredEventData) getEventData(certificate, context.eventContext().getData());
        eventData.setDiscoveryUuid(context.discoveryUuid());
        eventData.setDiscoveryName(context.discoveryName());
        eventData.setDiscoveryUserUuid(context.userUuid());
        eventData.setDiscoveryConnectorUuid(context.connectorUuid());
        eventData.setDiscoveryConnectorName(context.connectorName());

        // Every row carries its own per-host metadata; grouping deduplicates the certificate, not the metadata.
        group.rows().forEach(row -> certificateHandler.updateDiscoveredCertificate(DiscoverySource.of(context), certificate, row.getMeta()));

        for (TriggerAssociation triggerAssociation : context.triggers()) {
            EventHistory eventHistory = triggerAssociation.getResource() == null ? platformEventHistory : discoveryEventHistory;
            context.eventContext().getTriggerEvaluator().evaluateTrigger(triggerAssociation.getTrigger(), triggerAssociation,
                    certificate, referenceRowUuid, eventData, eventHistory);
        }

        return new GroupImportResult(group.certificateContentId(),
                resultsFor(rowUuids, DiscoveryCertificateOutcome.IMPORTED, null),
                keyEntriesFor(certificate, x509Cert, rowUuids), true);
    }

    private static List<KeyQueueEntry> keyEntriesFor(Certificate certificate, X509Certificate x509Cert,
                                                     List<UUID> rowUuids) {
        List<KeyQueueEntry> entries = new ArrayList<>();
        entries.add(KeyQueueEntry.of(x509Cert.getPublicKey(), false, certificate.getUuid(), rowUuids));

        byte[] altPublicKeyEncoded = x509Cert.getExtensionValue(Extension.subjectAltPublicKeyInfo.getId());
        if (altPublicKeyEncoded != null) {
            try {
                entries.add(KeyQueueEntry.of(CertificateUtil.getAltPublicKey(altPublicKeyEncoded), true,
                        certificate.getUuid(), rowUuids));
            } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
                // Losing this silently is how a run reported clean with the alternative key absent, so the
                // failure travels as an entry the key phase can report rather than only a log line.
                logger.error("Could not parse alternative public key of certificate {}: {}",
                        certificate.getUuid(), e.getMessage());
                entries.add(KeyQueueEntry.unparseable(true, certificate.getUuid(), rowUuids,
                        DiscoveryFailureReason.shape(e)));
            }
        }
        return entries;
    }

    public static EventMessage constructEventMessage(UUID discoveryUuid, UUID userUuid, ScheduledJobInfo scheduledJobInfo) {
        return new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.CERTIFICATE, null, Resource.DISCOVERY, discoveryUuid, null, userUuid, scheduledJobInfo);
    }
}
