package com.czertainly.core.dao.repository.workflows;

import com.czertainly.core.dao.entity.workflows.TriggerHistoryRecord;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TriggerHistoryRecordRepository extends SecurityFilterRepository<TriggerHistoryRecord, UUID> {
}
