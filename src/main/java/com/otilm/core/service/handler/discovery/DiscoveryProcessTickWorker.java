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
import com.otilm.core.service.writer.DiscoveryWriter;
import com.otilm.core.service.writer.discovery.DiscoveryWorkWriter;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
 * pipeline yet, so this worker neither claims them nor waits on them (core#1965). Counting rows it cannot process would
 * leave every run with keys spinning in {@code PROCESSING} forever.
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
    private final DiscoveryWorkProperties workProperties;
    private final int batchSize;
    private final Duration continuationBackstop;

    public DiscoveryProcessTickWorker(DiscoveryRepository discoveryRepository,
            DiscoveryCertificateRepository certificateRepository, CertificateDiscoveredEventHandler importHandler,
            DiscoveryWorkWriter workWriter, DiscoveryWorkProducer workProducer, DiscoveryRunTerminator terminator,
            DiscoveryWriter discoveryWriter, DiscoveryWorkProperties workProperties,
            @Value("${discovery.processing.batch-size:200}") int batchSize,
            @Value("${discovery.work.continuation-backstop:PT1M}") Duration continuationBackstop) {
        this.discoveryRepository = discoveryRepository;
        this.certificateRepository = certificateRepository;
        this.importHandler = importHandler;
        this.workWriter = workWriter;
        this.workProducer = workProducer;
        this.terminator = terminator;
        this.discoveryWriter = discoveryWriter;
        this.workProperties = workProperties;
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
        List<DiscoveryCertificate> batch = certificateRepository
                .findByDiscoveryUuidAndNewlyDiscoveredTrueAndProcessedFalseAndProcessedErrorIsNullOrderByCreatedAsc(
                        discoveryUuid, PageRequest.of(0, batchSize));
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
            DiscoveryRunCounts counts = importHandler.processBatch(run, batch);
            discoveryWriter.appendRunMessages(run.getUuid(), counts.describeGaps());
        } catch (Exception e) {
            // Swallowed rather than rethrown, and deliberately so. A throw here reaches the listener, which logs
            // and acknowledges, so the budget is never spent and a persistent pre-batch failure -- an
            // authorization refusal, a trigger that cannot be set up -- strands the run in PROCESSING forever,
            // the exact failure this worker exists to close. The batch stamped nothing, so the caller sees an
            // unchanged backlog and takes the stall path, which is bounded and ends the run with a reason.
            logger
                    .error("Processing batch {} of discovery {} did not complete: {}", attempt, run.getUuid(),
                            e.getMessage(), e);
            discoveryWriter
                    .appendRunMessages(run.getUuid(),
                            List.of("A batch of discovered certificates could not be processed."));
        }
    }

    /**
     * Commits the agenda row as the backstop for the next batch, then publishes that batch directly. The row is
     * committed first so a publish that never lands is still picked up, and one backstop interval out rather than
     * due-now so the sweep cannot publish a competing tick against the same run.
     */
    private void continueProcessing(UUID discoveryUuid, long remaining) {
        logger.debug("Discovery {} has {} certificates left to process", discoveryUuid, remaining);
        workWriter
                .reschedule(discoveryUuid, DiscoveryWorkType.PROCESS, 0,
                        OffsetDateTime.now(ZoneOffset.UTC).plus(continuationBackstop));
        workProducer.produceMessage(new DiscoveryWorkMessage(discoveryUuid, DiscoveryWorkType.PROCESS, 0));
    }

    /**
     * A tick that accounted for nothing. Rather than publishing again — which is how a stalled run becomes a tight loop
     * on the broker — the row climbs its backoff ladder, and once the budget is spent the run ends rather than sitting
     * in {@code PROCESSING} forever, which is the failure this worker exists to prevent.
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
     * Ends the run on the evidence its own rows carry: any row that recorded a reason it could not be imported makes
     * the run a WARNING, so a partial success is never reported as a clean one.
     */
    private void finish(UUID discoveryUuid) {
        // Two kinds of evidence, because not every shortfall lands on a row. A bookkeeping write that itself
        // failed, or validation never being requested, is a run-level gap with every row still clean -- reading
        // only the rows would report such a run as a clean success. On a v2 run every writer into the message
        // log is reporting a problem, so a non-empty log is exactly the run-level evidence needed.
        boolean anyFailed = certificateRepository.existsByDiscoveryUuidAndProcessedErrorIsNotNull(discoveryUuid)
                || discoveryRepository
                        .findByUuid(discoveryUuid)
                        .map(run -> run.getRunMessages() != null && !run.getRunMessages().isEmpty())
                        .orElse(false);
        if (anyFailed) {
            terminator
                    .end(discoveryUuid, DiscoveryStatus.WARNING,
                            "Discovery completed with warnings. See this run's messages, and the discovery "
                                    + "certificate list for per-certificate detail.");
        } else {
            terminator.end(discoveryUuid, DiscoveryStatus.COMPLETED, "Discovery completed successfully.");
        }
    }
}
