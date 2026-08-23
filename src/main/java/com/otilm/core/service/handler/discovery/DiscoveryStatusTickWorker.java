package com.otilm.core.service.handler.discovery;

import com.otilm.api.exception.AttributeException;
import com.otilm.api.exception.ConnectorException;
import com.otilm.api.exception.NotFoundException;
import com.otilm.api.model.connector.discovery.v2.DiscoveryRunState;
import com.otilm.api.model.connector.discovery.v2.DiscoveryStatusResponseDto;
import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.repository.DiscoveryRepository;
import com.otilm.core.events.transaction.TransactionHandler;
import com.otilm.core.messaging.jms.configuration.DiscoveryWorkProperties;
import com.otilm.core.model.discovery.DiscoveryRunLifecycle;
import com.otilm.core.model.discovery.DiscoveryWorkType;
import com.otilm.core.service.writer.discovery.DiscoveryWorkWriter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The {@code STATUS} tick: one authoritative {@code status} call, and the run state it justifies.
 *
 * <p>
 * <b>This is the only place a connector-reported state becomes Core state.</b> Pushed events merely ask for this tick;
 * what the connector answers here is what commits.
 *
 * <p>
 * <b>The attempt budget counts consecutive unanswered ticks, not elapsed time.</b> Any clear answer — the run is
 * running, the run is paused — pulls the counter back down to the ladder's ceiling, so a scan that takes a week and a
 * pause that lasts days both keep their budget as long as the connector keeps answering. The budget only runs out when
 * the connector stops answering at all, which is exactly the case where retrying forever is pointless.
 */
@Component
public class DiscoveryStatusTickWorker {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryStatusTickWorker.class);

    private final DiscoveryRepository discoveryRepository;
    private final DiscoveryV2Client client;
    private final DiscoveryWorkWriter workWriter;
    private final DiscoveryRunTerminator terminator;
    private final DiscoveryWorkProperties workProperties;
    private final TransactionHandler transactionHandler;

    public DiscoveryStatusTickWorker(DiscoveryRepository discoveryRepository, DiscoveryV2Client client,
            DiscoveryWorkWriter workWriter, DiscoveryRunTerminator terminator, DiscoveryWorkProperties workProperties,
            TransactionHandler transactionHandler) {
        this.discoveryRepository = discoveryRepository;
        this.client = client;
        this.workWriter = workWriter;
        this.terminator = terminator;
        this.workProperties = workProperties;
        this.transactionHandler = transactionHandler;
    }

    public void tick(UUID discoveryUuid, int attempt) {
        Discovery run = discoveryRepository.findByUuid(discoveryUuid).orElse(null);
        if (run == null) {
            // The agenda cascaded away with the run; this is a redelivery of an obsolete tick.
            logger.debug("Dropping status tick for discovery {}: the run no longer exists", discoveryUuid);
            return;
        }
        if (DiscoveryRunLifecycle.hasLeftTheConnector(run.getStatus())) {
            // Terminal, or handed over to processing: either way the connector no longer owns this run and its
            // handle is gone, so calling status would read a 404 as "the run vanished" and end a healthy import.
            logger.debug("Dropping status tick for discovery {}: already {}", discoveryUuid, run.getStatus());
            workWriter.deleteForRun(discoveryUuid, DiscoveryWorkType.STATUS);
            return;
        }

        DiscoveryStatusResponseDto status;
        try {
            // Outside any transaction, by the platform's connector-call rule.
            status = client.status(run);
        } catch (ConnectorException | RuntimeException e) {
            // RuntimeException too: the client's own javadoc warns its declared throws is incomplete — over MQ a
            // 422 arrives as an unchecked ValidationException, and a bodiless 2xx as IllegalStateException. Left
            // to escape, those reach the listener's log-and-acknowledge and the tick retries forever having spent
            // no budget, which is exactly the immortal run the budget exists to prevent.
            handleUnanswered(discoveryUuid, attempt, e);
            return;
        } catch (NotFoundException | AttributeException e) {
            // The call could not be assembled at all: the connector row or the run's attributes are gone.
            // Retrying cannot repair that, and the run has no way to make progress.
            terminator
                    .end(discoveryUuid, DiscoveryStatus.FAILED,
                            "The discovery run can no longer be addressed at its connector");
            return;
        }

        if (apply(discoveryUuid, status)) {
            // A clear answer refreshes the budget without restarting the backoff ramp: the counter drops to
            // the rung where the ladder already reached its slowest delay.
            workWriter
                    .resetAttempt(discoveryUuid, DiscoveryWorkType.STATUS,
                            workProperties.scheduleFor(DiscoveryWorkType.STATUS).ceilingAttempt());
        }
    }

    /**
     * Decides what an unanswered tick costs. A definitive refusal ends the run at once; otherwise the run keeps its
     * agenda row — the claimer already pushed it up the backoff ladder — until the budget is spent.
     */
    private void handleUnanswered(UUID discoveryUuid, int attempt, Throwable e) {
        if (DiscoveryConnectorErrors.isRunNoLongerTracked(e)) {
            terminator.end(discoveryUuid, DiscoveryStatus.FAILED, "The connector no longer tracks this run");
            return;
        }
        if (attempt + 1 >= workProperties.scheduleFor(DiscoveryWorkType.STATUS).maxAttempts()) {
            terminator
                    .end(discoveryUuid, DiscoveryStatus.FAILED,
                            "The connector stopped answering status polls for this run: "
                                    + DiscoveryConnectorErrors.describe(e));
            return;
        }
        logger
                .warn("Status poll {} for discovery {} failed, retrying when next due: {}", attempt, discoveryUuid,
                        e.getMessage());
    }

    /**
     * Commits what the connector reported.
     *
     * @return whether the run is still live and its budget should be refreshed — false once the answer was itself
     * terminal, where there is no longer an agenda row to refresh
     */
    private boolean apply(UUID discoveryUuid, DiscoveryStatusResponseDto status) {
        DiscoveryRunState state = status.getState();
        if (state == DiscoveryRunState.FAILED || state == DiscoveryRunState.CANCELLED) {
            terminator
                    .end(discoveryUuid, terminalStatusFor(state), "The connector reported the run " + state.getCode());
            return false;
        }
        return Boolean.TRUE.equals(transactionHandler.runInNewTransaction(() -> {
            Discovery locked = discoveryRepository.findWithLockByUuid(discoveryUuid).orElse(null);
            // Re-assert under the row lock: another tick may have ended the run, or the drain may have handed it
            // over to processing, while this status call was in flight.
            if (locked == null || DiscoveryRunLifecycle.hasLeftTheConnector(locked.getStatus())) {
                return false;
            }
            locked.setConnectorState(state.getCode());
            if (status.getProgress() != null) {
                locked.setProgress(status.getProgress());
            }
            applyLiveState(locked, state);
            return true;
        }));
    }

    private void applyLiveState(Discovery run, DiscoveryRunState state) {
        run.setConnectorStatus(connectorStatusFor(state));
        switch (state) {
            case RUNNING -> {
                run.setStatus(DiscoveryStatus.IN_PROGRESS);
                clearResumeWindow(run);
            }
            case STOPPED -> {
                run.setStatus(DiscoveryStatus.STOPPED);
                // Starts the resume window the reaper bounds. Stamped only when the run is not already
                // inside one, so repeated STOPPED answers cannot keep pushing the deadline out.
                if (run.getStoppedAt() == null) {
                    run.setStoppedAt(OffsetDateTime.now(ZoneOffset.UTC));
                }
            }
            // Core stays IN_PROGRESS through the tail drain and flips to PROCESSING only once the drain has
            // fully caught up -- entering PROCESSING with items still at the connector would strand them.
            case COMPLETED -> {
                run.setStatus(DiscoveryStatus.IN_PROGRESS);
                clearResumeWindow(run);
                workWriter.schedule(run.getUuid(), DiscoveryWorkType.DRAIN, OffsetDateTime.now(ZoneOffset.UTC));
            }
            default -> throw new IllegalStateException("Unhandled live discovery run state " + state);
        }
    }

    /**
     * A run that is no longer stopped carries no resume deadline. Leaving the old timestamp behind would let a much
     * later stop inherit an already-expired window and be auto-cancelled the moment it pauses.
     */
    private static void clearResumeWindow(Discovery run) {
        run.setStoppedAt(null);
    }

    private static DiscoveryStatus connectorStatusFor(DiscoveryRunState state) {
        return switch (state) {
            case RUNNING -> DiscoveryStatus.IN_PROGRESS;
            case STOPPED -> DiscoveryStatus.STOPPED;
            case COMPLETED -> DiscoveryStatus.COMPLETED;
            case FAILED -> DiscoveryStatus.FAILED;
            case CANCELLED -> DiscoveryStatus.CANCELLED;
        };
    }

    private static DiscoveryStatus terminalStatusFor(DiscoveryRunState state) {
        return state == DiscoveryRunState.CANCELLED ? DiscoveryStatus.CANCELLED : DiscoveryStatus.FAILED;
    }
}
