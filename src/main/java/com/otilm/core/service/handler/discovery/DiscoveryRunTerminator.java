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
     * @return whether this call was the one that ended the run
     */
    public boolean end(UUID discoveryUuid, DiscoveryStatus status, String reason) {
        return Boolean.TRUE.equals(transactionHandler.runInNewTransaction(() -> {
            Discovery run = discoveryRepository.findWithLockByUuid(discoveryUuid).orElse(null);
            if (run == null) {
                return false;
            }
            if (DiscoveryRunLifecycle.isTerminal(run.getStatus())) {
                logger.debug("Discovery {} is already {}; leaving it alone", discoveryUuid, run.getStatus());
                return false;
            }
            run.setStatus(status);
            run.setMessage(reason);
            run.setEndTime(OffsetDateTime.now(ZoneOffset.UTC));
            run.setRunMeta(null);
            run.setRunMessages(DiscoveryRunLifecycle.append(run.getRunMessages(), reason));
            workWriter.deleteForRun(discoveryUuid);
            logger.info("Discovery {} ended as {}: {}", discoveryUuid, status, reason);
            return true;
        }));
    }
}
