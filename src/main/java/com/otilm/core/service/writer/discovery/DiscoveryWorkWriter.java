package com.otilm.core.service.writer.discovery;

import com.otilm.core.dao.repository.DiscoveryWorkRepository;
import com.otilm.core.model.discovery.DiscoveryWorkType;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Short transactional writes against the {@code discovery_work} agenda. The repository carries no
 * {@code @Transactional} (per the repository rule); every {@code @Modifying} write goes through this bean. Methods are
 * {@code REQUIRED} so an agenda change commits or rolls back with the run state it supervises — except
 * {@link #resetAttempt}, whose callers run outside any usable transaction.
 */
@Component
public class DiscoveryWorkWriter {

    private final DiscoveryWorkRepository workRepository;

    public DiscoveryWorkWriter(DiscoveryWorkRepository workRepository) {
        this.workRepository = workRepository;
    }

    /**
     * Schedules the run's pending row for {@code workType}, due at {@code nextDueAt} — or re-arms the existing one (due
     * time moved, backoff counter reset). Scheduling is a fresh start; in-flight backoff belongs to
     * {@link #reschedule}.
     */
    @Transactional
    public void schedule(UUID discoveryUuid, DiscoveryWorkType workType, OffsetDateTime nextDueAt) {
        workRepository.schedule(UUID.randomUUID(), discoveryUuid, workType.name(), nextDueAt);
    }

    /**
     * Advances an agenda row's {@code attempt}/{@code next_due_at}. {@code REQUIRED} (not {@code REQUIRES_NEW}) so it
     * joins the sweep claimer's lock-holding transaction — a row is claimed and rescheduled atomically, or neither.
     */
    @Transactional
    public void reschedule(UUID discoveryUuid, DiscoveryWorkType workType, int attempt, OffsetDateTime nextDueAt) {
        workRepository.reschedule(discoveryUuid, workType, attempt, nextDueAt);
    }

    /**
     * Lowers an agenda row's attempt counter to {@code attempt} if it is above {@code attempt} — how a clear connector
     * answer refreshes the attempt budget without restarting the backoff ramp.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetAttempt(UUID discoveryUuid, DiscoveryWorkType workType, int attempt) {
        workRepository.resetAttemptTo(discoveryUuid, workType, attempt);
    }

    /**
     * Drops every agenda row of a run — the terminal-transition cleanup.
     */
    @Transactional
    public void deleteForRun(UUID discoveryUuid) {
        workRepository.deleteByDiscoveryUuid(discoveryUuid);
    }
}
