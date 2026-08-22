package com.otilm.core.service.handler.discovery;

import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.entity.DiscoveryCertificate;
import com.otilm.core.dao.repository.DiscoveryCertificateRepository;
import com.otilm.core.dao.repository.DiscoveryRepository;
import com.otilm.core.events.handlers.CertificateDiscoveredEventHandler;
import com.otilm.core.events.handlers.discovery.DiscoveryRunCounts;
import com.otilm.core.messaging.jms.producers.DiscoveryWorkProducer;
import com.otilm.core.messaging.model.DiscoveryWorkMessage;
import com.otilm.core.model.discovery.DiscoveryRunLifecycle;
import com.otilm.core.model.discovery.DiscoveryWorkType;
import com.otilm.core.service.writer.DiscoveryWriter;
import com.otilm.core.service.writer.discovery.DiscoveryWorkWriter;
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
 * rows the run has not marked processed — and a batch that dies is simply a batch that never stamped its rows. Any pod
 * picks the run up on the next tick and continues where the cursor stands.
 *
 * <p>
 * <b>One row of work, not a fan-out.</b> A run has exactly one {@code PROCESS} agenda row (the table's unique
 * constraint), so batches never run in parallel against the same run. The run chews through its backlog as a
 * self-perpetuating chain: each batch commits its rows, then publishes the tick for the next one.
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
    private final int batchSize;

    public DiscoveryProcessTickWorker(DiscoveryRepository discoveryRepository,
            DiscoveryCertificateRepository certificateRepository, CertificateDiscoveredEventHandler importHandler,
            DiscoveryWorkWriter workWriter, DiscoveryWorkProducer workProducer, DiscoveryRunTerminator terminator,
            DiscoveryWriter discoveryWriter, @Value("${discovery.processing.batch-size:200}") int batchSize) {
        this.discoveryRepository = discoveryRepository;
        this.certificateRepository = certificateRepository;
        this.importHandler = importHandler;
        this.workWriter = workWriter;
        this.workProducer = workProducer;
        this.terminator = terminator;
        this.discoveryWriter = discoveryWriter;
        this.batchSize = batchSize;
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

        List<DiscoveryCertificate> batch = certificateRepository
                .findByDiscoveryUuidAndNewlyDiscoveredTrueAndProcessedFalseOrderByCreatedAsc(discoveryUuid,
                        PageRequest.of(0, batchSize));
        if (!batch.isEmpty()) {
            importBatch(discoveryUuid, attempt, batch);
        }

        long remaining = certificateRepository
                .countByDiscoveryUuidAndNewlyDiscoveredTrueAndProcessedFalse(discoveryUuid);
        if (remaining > 0) {
            continueProcessing(discoveryUuid, remaining);
            return;
        }
        finish(discoveryUuid);
    }

    /**
     * Runs the batch through the unchanged import pipeline. A batch that fails wholesale leaves its rows unstamped, so
     * the next tick reclaims exactly them — but it also leaves the run no closer to finishing, so the failure is
     * recorded on the run where an operator can see it before the tick is retried.
     */
    private void importBatch(UUID discoveryUuid, int attempt, List<DiscoveryCertificate> batch) {
        Discovery run = discoveryRepository.findByUuid(discoveryUuid).orElseThrow();
        try {
            DiscoveryRunCounts counts = importHandler.processBatch(run, batch);
            // Filed as the batch finishes rather than summed up at the end: a long run's operator can see what
            // is going wrong while it is still going wrong, and the per-row reasons stay on the rows.
            discoveryWriter.appendRunMessages(discoveryUuid, counts.describeGaps());
        } catch (Exception e) {
            logger
                    .error("Processing batch {} of discovery {} did not complete: {}", attempt, discoveryUuid,
                            e.getMessage(), e);
            throw new IllegalStateException(
                    "Discovery " + discoveryUuid + " could not process a batch of discovered certificates", e);
        }
    }

    /**
     * Commits the row as due now, then publishes the next batch's tick directly. The row is committed first so a
     * publish that never lands still leaves the sweep something due to pick up.
     */
    private void continueProcessing(UUID discoveryUuid, long remaining) {
        logger.debug("Discovery {} has {} certificates left to process", discoveryUuid, remaining);
        workWriter.reschedule(discoveryUuid, DiscoveryWorkType.PROCESS, 0, OffsetDateTime.now(ZoneOffset.UTC));
        workProducer.produceMessage(new DiscoveryWorkMessage(discoveryUuid, DiscoveryWorkType.PROCESS, 0));
    }

    /**
     * Ends the run on the evidence its own rows carry: any row that recorded a reason it could not be imported makes
     * the run a WARNING, so a partial success is never reported as a clean one.
     */
    private void finish(UUID discoveryUuid) {
        boolean anyFailed = certificateRepository.existsByDiscoveryUuidAndProcessedErrorIsNotNull(discoveryUuid);
        if (anyFailed) {
            terminator
                    .end(discoveryUuid, DiscoveryStatus.WARNING,
                            "Discovery completed with warnings. See the discovery certificate list for "
                                    + "per-certificate detail.");
        } else {
            terminator.end(discoveryUuid, DiscoveryStatus.COMPLETED, "Discovery completed successfully.");
        }
    }
}
