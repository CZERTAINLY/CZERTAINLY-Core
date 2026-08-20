package com.otilm.core.messaging.jms.listeners.discovery;

import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.cluster.ClusterOperationSynchronizer;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.repository.DiscoveryRepository;
import com.otilm.core.dao.repository.DiscoveryWorkRepository;
import com.otilm.core.events.transaction.TransactionHandler;
import com.otilm.core.service.handler.discovery.DiscoveryProviderAdapterFactory;
import com.otilm.core.service.writer.discovery.DiscoveryWorkWriter;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Terminates discovery v2 runs the tick engine can no longer drive:
 *
 * <ul>
 * <li><b>Work lost</b> — a non-terminal v2 run with no agenda rows never receives another tick, so nothing would ever
 * end it. Failed with an explicit reason. Runs younger than {@code reap-grace} are skipped: the initiate window
 * legitimately has the run row before its first agenda rows.</li>
 * <li><b>Stop expired</b> — a stopped run not resumed within {@code stopped-max-duration} is cancelled; the connector's
 * checkpoint can no longer be assumed to exist. The connector-side cancel is best-effort and runs outside any
 * transaction; the local cancel proceeds regardless.</li>
 * </ul>
 *
 * <p>
 * Runs as a phase of {@link DiscoveryWorkSweeper#sweep()} (no separate schedule) and reuses that sweep's
 * {@code DISCOVERY_WORK_SWEEP} advisory lock for selection. Correctness across nodes rests on the per-run pessimistic
 * lock plus a re-assertion of the reap condition; the advisory lock only avoids redundant selection.
 * </p>
 */
@Component
public class DiscoveryRunReaper {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryRunReaper.class);

    private static final List<DiscoveryStatus> NON_TERMINAL_STATUSES = List
            .of(DiscoveryStatus.IN_PROGRESS, DiscoveryStatus.PROCESSING, DiscoveryStatus.STOPPED);

    private final DiscoveryRepository discoveryRepository;
    private final DiscoveryWorkRepository workRepository;
    private final DiscoveryWorkWriter workWriter;
    private final DiscoveryProviderAdapterFactory adapterFactory;
    private final TransactionHandler transactionHandler;
    private final ClusterOperationSynchronizer clusterSynchronizer;
    private final Duration reapGrace;
    private final Duration stoppedMaxDuration;
    private final int batchSize;

    public DiscoveryRunReaper(DiscoveryRepository discoveryRepository, DiscoveryWorkRepository workRepository,
            DiscoveryWorkWriter workWriter, DiscoveryProviderAdapterFactory adapterFactory,
            TransactionHandler transactionHandler, ClusterOperationSynchronizer clusterSynchronizer,
            @Value("${discovery.work.reap-grace:PT5M}") Duration reapGrace,
            @Value("${discovery.run.stopped-max-duration:P7D}") Duration stoppedMaxDuration,
            @Value("${discovery.work.sweep-batch-size:200}") int batchSize) {
        this.discoveryRepository = discoveryRepository;
        this.workRepository = workRepository;
        this.workWriter = workWriter;
        this.adapterFactory = adapterFactory;
        this.transactionHandler = transactionHandler;
        this.clusterSynchronizer = clusterSynchronizer;
        this.reapGrace = reapGrace;
        this.stoppedMaxDuration = stoppedMaxDuration;
        this.batchSize = batchSize;
    }

    /**
     * Selects one bounded batch per reap condition under the shared advisory lock, then handles each run in its own
     * transaction outside the lock. One batch per sweep is sufficient: the sweep cadence drains any backlog, and a run
     * is not a candidate until its grace or expiry window has passed.
     */
    public void reap() {
        int failed = 0;
        for (UUID uuid : selectUnderSweepLock(this::selectWorkLost)) {
            if (failWorkLost(uuid)) {
                failed++;
            }
        }
        int cancelled = 0;
        for (UUID uuid : selectUnderSweepLock(this::selectStopExpired)) {
            if (cancelExpiredStop(uuid)) {
                cancelled++;
            }
        }
        if (failed > 0 || cancelled > 0) {
            logger.info("Discovery run reap: {} failed (work lost), {} cancelled (stop expired)", failed, cancelled);
        }
    }

    private List<UUID> selectUnderSweepLock(Supplier<List<UUID>> selection) {
        return transactionHandler.runInNewTransaction(() -> {
            if (!clusterSynchronizer.tryLock(ClusterOperationSynchronizer.Operation.DISCOVERY_WORK_SWEEP)) {
                return List.of();
            }
            return selection.get();
        });
    }

    private List<UUID> selectWorkLost() {
        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC).minus(reapGrace);
        return discoveryRepository.findWorkLostRunUuids(NON_TERMINAL_STATUSES, threshold, PageRequest.of(0, batchSize));
    }

    private List<UUID> selectStopExpired() {
        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC).minus(stoppedMaxDuration);
        return discoveryRepository
                .findExpiredStoppedRunUuids(NON_TERMINAL_STATUSES, threshold, PageRequest.of(0, batchSize));
    }

    private boolean failWorkLost(UUID uuid) {
        try {
            return Boolean.TRUE.equals(transactionHandler.runInNewTransaction(() -> {
                Discovery run = discoveryRepository.findWithLockByUuid(uuid).orElse(null);
                // Re-assert under the row lock: the run may have finished, or gained agenda rows,
                // between selection and locking.
                if (run == null || !NON_TERMINAL_STATUSES.contains(run.getStatus())
                        || workRepository.existsByDiscoveryUuid(uuid)) {
                    return false;
                }
                endRun(run, DiscoveryStatus.FAILED, "Discovery work lost; the run can no longer be driven");
                return true;
            }));
        } catch (RuntimeException e) {
            // One run's failure (e.g. a lock timeout) must not abort the rest of the batch.
            logger.warn("Failed to reap work-lost discovery run {}", uuid, e);
            return false;
        }
    }

    private boolean cancelExpiredStop(UUID uuid) {
        // Best-effort connector-side cancel, outside any transaction: the connector may be unreachable
        // or may have dropped the run already, and neither must block the local cancel.
        try {
            discoveryRepository.findByUuid(uuid).ifPresent(run -> adapterFactory.forDiscovery(run).cancel(run));
        } catch (RuntimeException e) {
            logger.debug("Best-effort connector cancel failed for expired stopped run {}", uuid, e);
        }
        try {
            return Boolean.TRUE.equals(transactionHandler.runInNewTransaction(() -> {
                Discovery run = discoveryRepository.findWithLockByUuid(uuid).orElse(null);
                // Re-assert under the row lock: the run may have been resumed or finished in the meantime.
                if (run == null || run.getStoppedAt() == null || !NON_TERMINAL_STATUSES.contains(run.getStatus())) {
                    return false;
                }
                endRun(run, DiscoveryStatus.CANCELLED, "Stop expired: the run was not resumed in time");
                workWriter.deleteForRun(uuid);
                return true;
            }));
        } catch (RuntimeException e) {
            logger.warn("Failed to cancel expired stopped discovery run {}", uuid, e);
            return false;
        }
    }

    // Connector-side run context is nulled on every terminal transition; connectorStatus keeps the last
    // report — the connector never confirmed this ending.
    private void endRun(Discovery run, DiscoveryStatus status, String message) {
        run.setStatus(status);
        run.setMessage(message);
        run.setEndTime(OffsetDateTime.now(ZoneOffset.UTC));
        run.setRunMeta(null);
    }
}
