package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.ScheduledJobHistory;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ScheduledJobHistoryRepository extends SecurityFilterRepository<ScheduledJobHistory, UUID>{
    ScheduledJobHistory findTopByScheduledJobUuidOrderByJobExecutionDesc(UUID scheduledJobUuid);
    boolean existsByScheduledJobUuid(UUID scheduledJobUuid);
}
