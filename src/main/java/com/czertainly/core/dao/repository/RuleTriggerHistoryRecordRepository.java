package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.RuleTriggerHistoryRecord;
import org.springframework.stereotype.Repository;

@Repository
public interface RuleTriggerHistoryRecordRepository extends SecurityFilterRepository<RuleTriggerHistoryRecord, Long> {
}
