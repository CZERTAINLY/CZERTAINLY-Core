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
import java.util.function.Function;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ends a discovery v2 run, the one way every tick worker does it: sets the status and reason, releases the connector
 * handle, and deletes the run's agenda.
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
     * Ends a run on the strength of something the connector said. Refuses a run that has left the connector, which
     * {@link #end} accepts but a connector-driven ending must not.
     *
     * @return whether this call was the one that ended the run
     */
    public boolean endConnectorOwned(UUID discoveryUuid, DiscoveryStatus status, String reason) {
        return endIf(discoveryUuid, DiscoveryRunLifecycle::hasLeftTheConnector, run -> new Ending(status, reason));
    }

    /**
     * @return whether this call was the one that ended the run
     */
    public boolean end(UUID discoveryUuid, DiscoveryStatus status, String reason) {
        return endIf(discoveryUuid, DiscoveryRunLifecycle::isTerminal, run -> new Ending(status, reason));
    }

    /**
     * Ends a run on a decision taken under the run row's lock, against the locked entity.
     *
     * @param decide the ending to apply, or {@code null} to leave the run alone
     * @return whether this call was the one that ended the run
     */
    public boolean endWith(UUID discoveryUuid, Function<Discovery, Ending> decide) {
        return endIf(discoveryUuid, DiscoveryRunLifecycle::isTerminal, decide);
    }

    /**
     * @param alreadyPast states from which this ending is no longer the caller's to make
     */
    private boolean endIf(UUID discoveryUuid, Predicate<DiscoveryStatus> alreadyPast,
            Function<Discovery, Ending> decide) {
        return Boolean.TRUE.equals(transactionHandler.runInNewTransaction(() -> {
            Discovery run = discoveryRepository.findWithLockByUuid(discoveryUuid).orElse(null);
            if (run == null) {
                return false;
            }
            if (alreadyPast.test(run.getStatus())) {
                logger.debug("Discovery {} is already {}; leaving it alone", discoveryUuid, run.getStatus());
                return false;
            }
            Ending ending = decide.apply(run);
            if (ending == null) {
                logger.debug("Discovery {} is not ready to end after all; leaving it alone", discoveryUuid);
                return false;
            }
            applyTerminalState(run, ending.status(), ending.reason());
            workWriter.deleteForRun(discoveryUuid);
            return true;
        }));
    }

    /** The status and reason a run ends with. */
    public record Ending(DiscoveryStatus status, String reason) {
    }

    /**
     * Applies the terminal state without opening a transaction, for callers (the reaper, and the status tick) that
     * already hold the run row's lock and would deadlock against {@link #end}'s own.
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
