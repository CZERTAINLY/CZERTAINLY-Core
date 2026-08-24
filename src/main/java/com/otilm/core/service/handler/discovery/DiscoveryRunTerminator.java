package com.otilm.core.service.handler.discovery;

import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.repository.DiscoveryRepository;
import com.otilm.core.events.transaction.TransactionHandler;
import com.otilm.core.model.discovery.DiscoveryRunLifecycle;
import com.otilm.core.service.writer.discovery.DiscoveryWorkWriter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ends a discovery v2 run, the one way every tick worker does it.
 *
 * <p>
 * A terminal transition is three writes that must land together: the status and reason a user sees, the release of the
 * connector's run handle, and the deletion of the run's agenda so nothing ticks for it again. Splitting them across
 * call sites is how a run ends up finished but still scheduled, or finished while still holding a handle to a
 * connector-side run nobody will ever cancel.
 *
 * <p>
 * The transition re-asserts under the run row's lock that the run has not already ended. Two ticks of the same run can
 * be in flight at once — a direct-published continuation racing a sweep redelivery — and the first ending wins.
 */
@Component
public class DiscoveryRunTerminator {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryRunTerminator.class);

    private final DiscoveryRepository discoveryRepository;
    private final DiscoveryWorkWriter workWriter;
    private final TransactionHandler transactionHandler;

    public DiscoveryRunTerminator(DiscoveryRepository discoveryRepository, DiscoveryWorkWriter workWriter,
            TransactionHandler transactionHandler) {
        this.discoveryRepository = discoveryRepository;
        this.workWriter = workWriter;
        this.transactionHandler = transactionHandler;
    }

    /**
     * Ends a run on the strength of something the connector said.
     *
     * <p>
     * Refuses a run that has left the connector, which {@link #end} cannot do for itself: processing legitimately ends
     * a {@code PROCESSING} run, so the plain entry point has to accept that state. A connector-driven ending must not.
     * A status or drain request started before the handover can return 404 after it, and treating that as "the run no
     * longer exists" would fail a healthy import and delete the agenda row driving it.
     *
     * @return whether this call was the one that ended the run
     */
    public boolean endConnectorOwned(UUID discoveryUuid, DiscoveryStatus status, String reason) {
        return endIf(discoveryUuid, status, reason, DiscoveryRunLifecycle::hasLeftTheConnector);
    }

    /**
     * @return whether this call was the one that ended the run
     */
    public boolean end(UUID discoveryUuid, DiscoveryStatus status, String reason) {
        return endIf(discoveryUuid, status, reason, DiscoveryRunLifecycle::isTerminal);
    }

    /**
     * @param alreadyPast states from which this ending is no longer the caller's to make
     */
    private boolean endIf(UUID discoveryUuid, DiscoveryStatus status, String reason,
            Predicate<DiscoveryStatus> alreadyPast) {
        return Boolean.TRUE.equals(transactionHandler.runInNewTransaction(() -> {
            Discovery run = discoveryRepository.findWithLockByUuid(discoveryUuid).orElse(null);
            if (run == null) {
                return false;
            }
            if (alreadyPast.test(run.getStatus())) {
                logger.debug("Discovery {} is already {}; leaving it alone", discoveryUuid, run.getStatus());
                return false;
            }
            applyTerminalState(run, status, reason);
            workWriter.deleteForRun(discoveryUuid);
            return true;
        }));
    }

    /**
     * Applies the terminal state to a run the caller already holds locked, without opening a transaction or
     * re-asserting anything.
     *
     * <p>
     * For callers that reached their decision inside their own locked transaction — the reaper, and the status tick
     * committing a connector-reported ending — {@link #end} is not available: it takes the same row lock and would
     * deadlock against the one already held. Such a caller owes the rest of the terminal transition itself, which for
     * both of them means deleting the run's agenda in that same transaction.
     */
    public void applyTerminalState(Discovery run, DiscoveryStatus status, String reason) {
        run.setStatus(status);
        run.setMessage(reason);
        run.setEndTime(OffsetDateTime.now(ZoneOffset.UTC));
        run.setRunMeta(null);
        run.setRunMessages(DiscoveryRunLifecycle.append(run.getRunMessages(), reason));
        logger.info("Discovery {} ended as {}: {}", run.getUuid(), status, reason);
    }
}
