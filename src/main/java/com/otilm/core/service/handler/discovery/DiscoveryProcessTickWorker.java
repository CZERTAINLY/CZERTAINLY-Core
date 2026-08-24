package com.otilm.core.service.handler.discovery;

import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.entity.DiscoveryCertificate;
import com.otilm.core.dao.repository.DiscoveryCertificateRepository;
import com.otilm.core.dao.repository.DiscoveryRepository;
import com.otilm.core.events.handlers.CertificateDiscoveredEventHandler;
import com.otilm.core.events.handlers.discovery.DiscoveryRunCounts;
import com.otilm.core.messaging.jms.configuration.DiscoveryWorkProperties;
import com.otilm.core.messaging.jms.producers.DiscoveryWorkProducer;
import com.otilm.core.messaging.model.DiscoveryWorkMessage;
import com.otilm.core.model.discovery.DiscoveryRunLifecycle;
import com.otilm.core.model.discovery.DiscoveryWorkType;
import com.otilm.core.service.handler.discovery.DiscoveryRunTerminator.Ending;
import com.otilm.core.service.writer.DiscoveryWriter;
import com.otilm.core.service.writer.discovery.DiscoveryWorkWriter;
import com.otilm.core.util.AuthHelper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * The {@code PROCESS} tick: one bounded batch of staged rows through the import pipeline, then either another tick or
 * the end of the run.
 *
 * <p>
 * <b>Why this exists at all.</b> The v1 flow processed a whole run inside one message handler, so a pod that died
 * halfway left the run stuck in {@code PROCESSING} forever with no way back. Here the backlog is a database cursor —
 * rows no batch has accounted for — and a batch that dies is simply a batch that never recorded an outcome. Any pod
 * picks the run up on the next tick and continues where the cursor stands.
 *
 * <p>
 * <b>Scope:</b> certificates only. Keys and every future resource are staged by the ingestor but have no import
 * pipeline yet, so this worker skips them; counting them against the backlog would leave every run with keys spinning
 * in {@code PROCESSING} forever.
 */
@Component
public class DiscoveryProcessTickWorker {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryProcessTickWorker.class);

    private final DiscoveryRepository discoveryRepository;
    private final DiscoveryCertificateRepository certificateRepository;
    private final CertificateDiscoveredEventHandler importHandler;
    private final DiscoveryWorkWriter workWriter;
    private final DiscoveryWorkProducer workProducer;
    private final DiscoveryRunTerminator terminator;
    private final DiscoveryWriter discoveryWriter;
    private final AuthHelper authHelper;
    private final DiscoveryWorkProperties workProperties;
    private final int batchSize;
    private final Duration continuationBackstop;

    public DiscoveryProcessTickWorker(DiscoveryRepository discoveryRepository,
            DiscoveryCertificateRepository certificateRepository, CertificateDiscoveredEventHandler importHandler,
            DiscoveryWorkWriter workWriter, DiscoveryWorkProducer workProducer, DiscoveryRunTerminator terminator,
            DiscoveryWriter discoveryWriter, AuthHelper authHelper, DiscoveryWorkProperties workProperties,
            @Value("${discovery.processing.batch-size:200}") int batchSize,
            @Value("${discovery.work.continuation-backstop:PT1M}") Duration continuationBackstop) {
        this.discoveryRepository = discoveryRepository;
        this.certificateRepository = certificateRepository;
        this.importHandler = importHandler;
        this.workWriter = workWriter;
        this.workProducer = workProducer;
        this.terminator = terminator;
        this.discoveryWriter = discoveryWriter;
        this.authHelper = authHelper;
        this.workProperties = workProperties;
        // Validated at construction, not at the first tick: a non-positive size throws inside PageRequest.of, and
        // left to a tick that throw would reach the listener's log-and-acknowledge and leave the run in
        // PROCESSING forever.
        if (batchSize <= 0) {
            throw new IllegalArgumentException("discovery.processing.batch-size must be positive");
        }
        // Must be positive: see DiscoveryWorkWriter's backstop invariant -- a due-now value races the sweep into
        // publishing a duplicate tick against the same unclaimed batch.
        if (continuationBackstop.isZero() || continuationBackstop.isNegative()) {
            throw new IllegalArgumentException("discovery.work.continuation-backstop must be positive");
        }
        this.batchSize = batchSize;
        this.continuationBackstop = continuationBackstop;
    }

    public void tick(UUID discoveryUuid, int attempt) {
        Discovery run = discoveryRepository.findByUuid(discoveryUuid).orElse(null);
        if (run == null) {
            logger.debug("Dropping process tick for discovery {}: the run no longer exists", discoveryUuid);
            return;
        }
        if (run.getStatus() != DiscoveryStatus.PROCESSING) {
            // Processing is entered once, by the drain's handover. A tick that finds the run anywhere else is a
            // redelivery from before the run ended, or from before it got here.
            logger
                    .debug("Dropping process tick for discovery {}: the run is {}, not PROCESSING", discoveryUuid,
                            run.getStatus());
            if (DiscoveryRunLifecycle.isTerminal(run.getStatus())) {
                workWriter.deleteForRun(discoveryUuid);
            }
            return;
        }

        long before = backlogOf(discoveryUuid);
        List<Long> contents = selectPendingContents(discoveryUuid);
        List<DiscoveryCertificate> batch = contents.isEmpty()
                ? List.of()
                : certificateRepository
                        .findByDiscoveryUuidAndCertificateContentIdInAndNewlyDiscoveredTrueAndProcessedFalseAndProcessedErrorIsNull(
                                discoveryUuid, contents);
        if (!batch.isEmpty()) {
            importBatch(run, attempt, batch);
        }

        long remaining = backlogOf(discoveryUuid);
        if (remaining == 0) {
            finish(discoveryUuid);
        } else if (remaining < before) {
            continueProcessing(discoveryUuid, remaining);
        } else {
            stall(discoveryUuid, attempt, remaining);
        }
    }

    /**
     * Picks the contents this tick will import, bounded by the rows they carry rather than by how many contents they
     * are. Selects, but does not claim: nothing marks these rows as taken, so a concurrent tick would select them too.
     *
     * <p>
     * A content's rows must travel together, or the pipeline runs that group's triggers and histories once per page —
     * so a certificate found on more hosts than the whole budget is taken alone rather than split, making the bound
     * exact for any batch of ordinary groups and best-effort for a single oversized one.
     */
    private List<Long> selectPendingContents(UUID discoveryUuid) {
        List<Object[]> pending = certificateRepository
                .findPendingContentWeights(discoveryUuid, PageRequest.of(0, batchSize));
        List<Long> selected = new ArrayList<>();
        long rows = 0;
        for (Object[] candidate : pending) {
            long weight = ((Number) candidate[1]).longValue();
            if (!selected.isEmpty() && rows + weight > batchSize) {
                break;
            }
            selected.add((Long) candidate[0]);
            rows += weight;
        }
        return selected;
    }

    private long backlogOf(UUID discoveryUuid) {
        return certificateRepository
                .countByDiscoveryUuidAndNewlyDiscoveredTrueAndProcessedFalseAndProcessedErrorIsNull(discoveryUuid);
    }

    /**
     * Runs the batch through the unchanged import pipeline and files what it could not import on the run, so an
     * operator watching a long run sees the trouble while it is still happening.
     */
    private void importBatch(Discovery run, int attempt, List<DiscoveryCertificate> batch) {
        try {
            authenticateAsTheRunsUser(run);
            DiscoveryRunCounts counts = importHandler.processBatch(run, batch);
            discoveryWriter.appendRunMessages(run.getUuid(), counts.describeGaps());
        } catch (Exception e) {
            // Swallowed, not rethrown: escaping to the listener's log-and-acknowledge would spend no budget and
            // strand the run in PROCESSING forever on a persistent pre-batch failure (an authorization refusal, a
            // trigger that cannot be set up). The batch stamped nothing, so the caller's unchanged backlog takes
            // the bounded stall path instead.
            logger
                    .error("Processing batch {} of discovery {} did not complete: {}", attempt, run.getUuid(),
                            e.getMessage(), e);
            discoveryWriter
                    .appendRunMessages(run.getUuid(),
                            List.of("A batch of discovered certificates could not be processed."));
        }
    }

    /**
     * Puts the run's own user on the thread before the import pipeline asks whether it may create certificates.
     *
     * <p>
     * A tick arrives on a JMS thread with no principal, and the pipeline enforces {@code CERTIFICATE:CREATE} against
     * whatever is on the thread — so without this every batch is refused, and because {@link #importBatch} swallows to
     * reach the bounded stall path, the refusal would surface as a run that quietly imported nothing. The v1 flow gets
     * the same identity from the user its {@code CERTIFICATE_DISCOVERED} event carries.
     *
     * <p>
     * A run with no user recorded cannot be imported at all, so it is left to the stall path rather than retried: no
     * later tick will find an identity the run never had.
     */
    private void authenticateAsTheRunsUser(Discovery run) {
        if (run.getStartedByUserUuid() == null) {
            throw new IllegalStateException(
                    "Discovery %s records no user to act as, so its certificates cannot be imported"
                            .formatted(run.getUuid()));
        }
        authHelper.authenticateAsUser(run.getStartedByUserUuid());
    }

    /**
     * Commits the agenda row as the backstop for the next batch, then publishes that batch directly. Committed first so
     * a publish that never lands is still picked up; see {@link DiscoveryWorkWriter} for why the row is due one
     * backstop interval out.
     */
    private void continueProcessing(UUID discoveryUuid, long remaining) {
        logger.debug("Discovery {} has {} certificates left to process", discoveryUuid, remaining);
        // The run's progress message, which the import pipeline cannot write for a v2 run: it sees one bounded
        // batch, and a percentage of that batch reaches 100% once per batch while the run still has a backlog.
        // Reported as what is left rather than as a fraction, since the backlog is what a tick actually measures.
        discoveryWriter
                .updateProgressMessage(discoveryUuid,
                        "Importing discovered certificates (%d remaining)".formatted(remaining));
        workWriter
                .reschedule(discoveryUuid, DiscoveryWorkType.PROCESS, 0,
                        OffsetDateTime.now(ZoneOffset.UTC).plus(continuationBackstop));
        workProducer.produceMessage(new DiscoveryWorkMessage(discoveryUuid, DiscoveryWorkType.PROCESS, 0));
    }

    /**
     * A tick that accounted for nothing. Rather than publishing again — which would turn a stalled run into a tight
     * loop on the broker — the row climbs its backoff ladder until the budget ends the run instead.
     */
    private void stall(UUID discoveryUuid, int attempt, long remaining) {
        int next = attempt + 1;
        if (next >= workProperties.scheduleFor(DiscoveryWorkType.PROCESS).maxAttempts()) {
            terminator
                    .end(discoveryUuid, DiscoveryStatus.WARNING,
                            ("Processing stopped with %d certificate(s) that could not be imported. See this run's "
                                    + "messages for what went wrong.").formatted(remaining));
            return;
        }
        logger
                .warn("Process tick {} for discovery {} accounted for none of its {} remaining certificates; backing "
                        + "off", attempt, discoveryUuid, remaining);
        workWriter
                .reschedule(discoveryUuid, DiscoveryWorkType.PROCESS, next,
                        OffsetDateTime
                                .now(ZoneOffset.UTC)
                                .plus(workProperties.scheduleFor(DiscoveryWorkType.PROCESS).delayFor(next)));
    }

    /**
     * Ends the run, or discovers it is not finished after all and keeps processing.
     */
    private void finish(UUID discoveryUuid) {
        if (!terminator.endWith(discoveryUuid, this::decideEnding)) {
            long late = backlogOf(discoveryUuid);
            if (late > 0) {
                continueProcessing(discoveryUuid, late);
            }
        }
    }

    /**
     * Decides the run's ending from inside the terminal transaction, where the run row is locked.
     *
     * <p>
     * Every input is read here rather than by the caller, and that is the point: a drain page in flight across the
     * handover takes the same lock, and it can both stage rows and append to the run's message log. Deciding outside
     * would let it land between the reads and the ending — a run reported as completed successfully while carrying a
     * warning, or ended while rows it staged sit counted by nobody.
     *
     * @return the ending, or {@code null} when a late page left work to do
     */
    private Ending decideEnding(Discovery run) {
        if (backlogOf(run.getUuid()) > 0) {
            return null;
        }
        // Two kinds of evidence, because not every shortfall lands on a row. A bookkeeping write that itself
        // failed, or validation never being requested, is a run-level gap with every row still clean -- reading
        // only the rows would report such a run as a clean success. On a v2 run every writer into the message
        // log is reporting a problem, so a non-empty log is exactly the run-level evidence needed.
        boolean rowsFailed = certificateRepository.existsByDiscoveryUuidAndProcessedErrorIsNotNull(run.getUuid());
        boolean runLevelGaps = run.getRunMessages() != null && !run.getRunMessages().isEmpty();
        if (!rowsFailed && !runLevelGaps) {
            return new Ending(DiscoveryStatus.COMPLETED, "Discovery completed successfully.");
        }
        // Sends the operator only where there is something to find: a run warned on run-level evidence alone
        // has no row carrying a reason.
        return new Ending(DiscoveryStatus.WARNING, rowsFailed
                ? "Discovery completed with warnings. See this run's messages, and the discovery certificate list "
                        + "for per-certificate detail."
                : "Discovery completed with warnings. See this run's messages.");
    }
}
