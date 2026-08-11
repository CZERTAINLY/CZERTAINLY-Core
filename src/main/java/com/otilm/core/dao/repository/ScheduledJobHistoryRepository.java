package com.otilm.core.dao.repository;

import com.otilm.api.model.scheduler.SchedulerJobExecutionStatus;
import com.otilm.core.dao.entity.ScheduledJobHistory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduledJobHistoryRepository extends SecurityFilterRepository<ScheduledJobHistory, UUID> {
    ScheduledJobHistory findTopByScheduledJobUuidOrderByJobExecutionDesc(UUID scheduledJobUuid);

    boolean existsByScheduledJobUuid(UUID scheduledJobUuid);

    boolean existsByScheduledJobUuidAndSchedulerExecutionStatusAndJobEndTimeIsNull(UUID scheduledJobUuid,
            SchedulerJobExecutionStatus schedulerExecutionStatus);

    Optional<ScheduledJobHistory> findFirstByScheduledJobJobNameAndSchedulerExecutionStatusOrderByJobExecutionDesc(
            String jobName, SchedulerJobExecutionStatus status);
}
