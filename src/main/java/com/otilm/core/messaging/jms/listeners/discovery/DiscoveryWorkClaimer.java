package com.otilm.core.messaging.jms.listeners.discovery;

import com.otilm.core.cluster.ClusterOperationSynchronizer;
import com.otilm.core.dao.entity.DiscoveryWork;
import com.otilm.core.dao.repository.DiscoveryWorkRepository;
import com.otilm.core.messaging.jms.configuration.DiscoveryWorkProperties;
import com.otilm.core.messaging.model.DiscoveryWorkMessage;
import com.otilm.core.service.writer.discovery.DiscoveryWorkWriter;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional claim half of the discovery work sweep, kept in its own bean so the {@link Transactional} boundary is
 * honoured (a self-invoked {@code @Transactional} method on {@link DiscoveryWorkSweeper} would be silently skipped by
 * the Spring proxy).
 *
 * <p>
 * One claim runs entirely against the database — no external call — so the advisory lock and the transaction are held
 * only for the duration of the read and the reschedules. The reschedule commits with the same transaction that holds
 * the lock, so a row is either claimed-and-rescheduled or neither.
 * </p>
 */
@Component
public class DiscoveryWorkClaimer {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryWorkClaimer.class);

    private final ClusterOperationSynchronizer clusterSynchronizer;
    private final DiscoveryWorkRepository workRepository;
    private final DiscoveryWorkWriter workWriter;
    private final DiscoveryWorkProperties workProperties;

    public DiscoveryWorkClaimer(ClusterOperationSynchronizer clusterSynchronizer,
            DiscoveryWorkRepository workRepository, DiscoveryWorkWriter workWriter,
            DiscoveryWorkProperties workProperties) {
        this.clusterSynchronizer = clusterSynchronizer;
        this.workRepository = workRepository;
        this.workWriter = workWriter;
        this.workProperties = workProperties;
    }

    /**
     * Claims up to {@code batchSize} rows due at {@code dueCutoff}: advances each row's
     * {@code attempt}/{@code next_due_at} by the backoff curve and returns the tick message to send for each. Returns
     * empty when another node already holds the sweep lock. The returned messages must be sent by the caller after this
     * method returns (outside the lock/transaction). The caller passes one cutoff for its whole sweep, so a row this
     * sweep already claimed — rescheduled at least the first rung past its claim — can never be claimed twice by the
     * same sweep, however long the publishing between batches takes.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<DiscoveryWorkMessage> claimDueBatch(int batchSize, OffsetDateTime dueCutoff) {
        if (!clusterSynchronizer.tryLock(ClusterOperationSynchronizer.Operation.DISCOVERY_WORK_SWEEP)) {
            logger.debug("Discovery work sweep skipped: another instance holds the lock");
            return List.of();
        }
        List<DiscoveryWork> due = workRepository
                .findByNextDueAtLessThanEqualOrderByNextDueAt(dueCutoff, PageRequest.of(0, batchSize));
        List<DiscoveryWorkMessage> messages = new ArrayList<>(due.size());
        for (DiscoveryWork work : due) {
            messages.add(new DiscoveryWorkMessage(work.getDiscoveryUuid(), work.getWorkType(), work.getAttempt()));

            int nextAttempt = work.getAttempt() + 1;
            Duration nextDelay = workProperties.scheduleFor(work.getWorkType()).delayFor(nextAttempt);
            workWriter
                    .reschedule(work.getDiscoveryUuid(), work.getWorkType(), nextAttempt,
                            OffsetDateTime.now(ZoneOffset.UTC).plus(nextDelay));
        }
        return messages;
    }
}
