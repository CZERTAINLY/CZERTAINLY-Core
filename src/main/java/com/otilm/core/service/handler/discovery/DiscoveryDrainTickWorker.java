package com.otilm.core.service.handler.discovery;

import com.otilm.api.exception.AttributeException;
import com.otilm.api.exception.ConnectorException;
import com.otilm.api.exception.NotFoundException;
import com.otilm.api.model.connector.discovery.v2.DiscoveryResultsResponseDto;
import com.otilm.api.model.connector.discovery.v2.DiscoveryRunState;
import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.repository.DiscoveryRepository;
import com.otilm.core.events.transaction.TransactionHandler;
import com.otilm.core.messaging.jms.configuration.DiscoveryWorkProperties;
import com.otilm.core.messaging.jms.producers.DiscoveryWorkProducer;
import com.otilm.core.messaging.model.DiscoveryWorkMessage;
import com.otilm.core.model.discovery.DiscoveryRunLifecycle;
import com.otilm.core.model.discovery.DiscoveryWorkType;
import com.otilm.core.service.writer.discovery.DiscoveryWorkWriter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The {@code DRAIN} tick: one bounded page of results, ingested, then a decision about what comes next.
 *
 * <p>
 * <b>Continuations are direct-published and table-backstopped.</b> When the connector says more items are waiting, this
 * worker commits the cursor advance and the agenda row's new due time, then publishes the follow-up tick itself. The
 * sweep's cadence is therefore the recovery latency after a lost message or a dead pod, never the throughput ceiling. A
 * duplicate from the publish-and-sweep overlap costs nothing: the cursor filter makes a repeated page a no-op.
 *
 * <p>
 * <b>The handover to processing needs a full acknowledgement, not just an empty page.</b> The connector reporting
 * {@code completed} and this page reporting {@code more: false} together still allow the run's cursor to sit below the
 * run-wide {@code highestSequence} — items the connector has produced but not yet handed over. Swapping to
 * {@code PROCESSING} there would strand them permanently, so the swap happens only once the cursor has caught up, and
 * anything short of that drains again immediately.
 */
@Component
public class DiscoveryDrainTickWorker {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryDrainTickWorker.class);

    private final DiscoveryRepository discoveryRepository;
    private final DiscoveryV2Client client;
    private final DiscoveryEventIngestor ingestor;
    private final DiscoveryWorkWriter workWriter;
    private final DiscoveryWorkProducer workProducer;
    private final DiscoveryRunTerminator terminator;
    private final DiscoveryWorkProperties workProperties;
    private final TransactionHandler transactionHandler;
    private final int maxItems;
    private final long maxBytes;

    public DiscoveryDrainTickWorker(DiscoveryRepository discoveryRepository, DiscoveryV2Client client,
            DiscoveryEventIngestor ingestor, DiscoveryWorkWriter workWriter, DiscoveryWorkProducer workProducer,
            DiscoveryRunTerminator terminator, DiscoveryWorkProperties workProperties,
            TransactionHandler transactionHandler, @Value("${discovery.drain.max-items:500}") int maxItems,
            @Value("${discovery.drain.max-bytes:5242880}") long maxBytes) {
        this.discoveryRepository = discoveryRepository;
        this.client = client;
        this.ingestor = ingestor;
        this.workWriter = workWriter;
        this.workProducer = workProducer;
        this.terminator = terminator;
        this.workProperties = workProperties;
        this.transactionHandler = transactionHandler;
        this.maxItems = maxItems;
        this.maxBytes = maxBytes;
    }

    public void tick(UUID discoveryUuid, int attempt) {
        Discovery run = discoveryRepository.findByUuid(discoveryUuid).orElse(null);
        if (run == null) {
            logger.debug("Dropping drain tick for discovery {}: the run no longer exists", discoveryUuid);
            return;
        }
        if (DiscoveryRunLifecycle.isTerminal(run.getStatus()) || run.getStatus() == DiscoveryStatus.PROCESSING) {
            // Nothing is left at the connector once processing owns the run.
            logger.debug("Dropping drain tick for discovery {}: already {}", discoveryUuid, run.getStatus());
            workWriter.deleteForRun(discoveryUuid);
            return;
        }

        DiscoveryResultsResponseDto page;
        try {
            // Outside any transaction, by the platform's connector-call rule.
            page = client.results(run, maxItems, maxBytes);
        } catch (ConnectorException e) {
            handleUnanswered(discoveryUuid, attempt, e);
            return;
        } catch (NotFoundException | AttributeException e) {
            terminator
                    .end(discoveryUuid, DiscoveryStatus.FAILED,
                            "The discovery run can no longer be addressed at its connector");
            return;
        }

        ingestor.applyDrainPage(discoveryUuid, page);
        continueAfter(discoveryUuid, run, page);
    }

    /**
     * Decides the run's next drain step from what the page said, and — where the answer is "there is more, now" —
     * publishes that step rather than waiting for a sweep to notice.
     */
    private void continueAfter(UUID discoveryUuid, Discovery run, DiscoveryResultsResponseDto page) {
        if (Boolean.TRUE.equals(page.getMore())) {
            drainAgain(discoveryUuid, "the connector reports more items");
            return;
        }
        if (!DiscoveryRunState.COMPLETED.getCode().equals(run.getConnectorState())) {
            // The connector is still producing. Nothing more is ready this moment, so fall back to the
            // ladder's slowest cadence with the budget refreshed by this successful page.
            idleUntilNextDue(discoveryUuid);
            return;
        }
        if (swapToProcessing(discoveryUuid, page.getHighestSequence())) {
            workProducer.produceMessage(new DiscoveryWorkMessage(discoveryUuid, DiscoveryWorkType.PROCESS, 0));
            return;
        }
        drainAgain(discoveryUuid, "the run has items the connector has not handed over yet");
    }

    /**
     * Commits the handover to Core-side processing, but only once every item the connector counted has been ingested.
     *
     * @return whether the swap happened; false means the cursor is still behind and the run must drain again
     */
    private boolean swapToProcessing(UUID discoveryUuid, Long highestSequence) {
        return Boolean.TRUE.equals(transactionHandler.runInNewTransaction(() -> {
            Discovery locked = discoveryRepository.findWithLockByUuid(discoveryUuid).orElse(null);
            if (locked == null || DiscoveryRunLifecycle.isTerminal(locked.getStatus())) {
                return false;
            }
            long acknowledged = locked.getLastAppliedSequence();
            if (highestSequence != null && acknowledged < highestSequence) {
                logger
                        .info("Discovery {} drained to {} of {} items; staying in the drain", discoveryUuid,
                                acknowledged, highestSequence);
                return false;
            }
            locked.setStatus(DiscoveryStatus.PROCESSING);
            // The connector owns nothing from here on, so its run handle is released with the same write
            // that hands the run to processing.
            locked.setRunMeta(null);
            // Delete then schedule inside one transaction: a live run's agenda must never be observably
            // empty, or the reaper would read it as lost work.
            workWriter.deleteForRun(discoveryUuid);
            workWriter.schedule(discoveryUuid, DiscoveryWorkType.PROCESS, OffsetDateTime.now(ZoneOffset.UTC));
            logger.info("Discovery {} drained {} items in full; processing them now", discoveryUuid, acknowledged);
            return true;
        }));
    }

    /**
     * Commits the row as due now — which also clears the attempt counter this successful page earned back — and then
     * publishes the follow-up tick. The row is committed first so a publish that never lands still leaves the sweep a
     * due row to pick up.
     */
    private void drainAgain(UUID discoveryUuid, String why) {
        logger.debug("Draining discovery {} again immediately: {}", discoveryUuid, why);
        workWriter.reschedule(discoveryUuid, DiscoveryWorkType.DRAIN, 0, OffsetDateTime.now(ZoneOffset.UTC));
        workProducer.produceMessage(new DiscoveryWorkMessage(discoveryUuid, DiscoveryWorkType.DRAIN, 0));
    }

    /**
     * Refreshes the budget a successful page earned without speeding the cadence up: the counter drops to the rung
     * where the ladder already reached its slowest delay, exactly as a clear status answer does.
     */
    private void idleUntilNextDue(UUID discoveryUuid) {
        workWriter
                .resetAttempt(discoveryUuid, DiscoveryWorkType.DRAIN,
                        workProperties.scheduleFor(DiscoveryWorkType.DRAIN).ceilingAttempt());
    }

    private void handleUnanswered(UUID discoveryUuid, int attempt, ConnectorException e) {
        if (DiscoveryConnectorErrors.isRunNoLongerTracked(e)) {
            terminator.end(discoveryUuid, DiscoveryStatus.FAILED, "The connector no longer tracks this run");
            return;
        }
        if (attempt + 1 >= workProperties.scheduleFor(DiscoveryWorkType.DRAIN).maxAttempts()) {
            terminator
                    .end(discoveryUuid, DiscoveryStatus.FAILED,
                            "The connector stopped handing over discovered items for this run: "
                                    + DiscoveryConnectorErrors.describe(e));
            return;
        }
        logger
                .warn("Drain {} for discovery {} failed, retrying when next due: {}", attempt, discoveryUuid,
                        e.getMessage());
    }
}
