package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.ScheduledJobHistory;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduledJobHistoryRepository extends SecurityFilterRepository<ScheduledJobHistory, UUID>{
    ScheduledJobHistory findTopByScheduledJobUuidOrderByJobExecutionDesc(UUID scheduledJobUuid);
    boolean existsByScheduledJobUuid(UUID scheduledJobUuid);

    @Query("SELECT sjh FROM ScheduledJobHistory sjh WHERE sjh.jobName = :jobName " +
           "AND sjh.schedulerExecutionStatus IN (com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus.STARTED, " +
           "com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus.SUCCESS) " +
           "ORDER BY sjh.jobExecution DESC LIMIT 1")
    Optional<ScheduledJobHistory> findLastStartedOrSucceededByJobName(@Param("jobName") String jobName);
}
