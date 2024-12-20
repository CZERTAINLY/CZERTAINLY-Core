package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.AuditLog;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends SecurityFilterRepository<AuditLog, Long> {
}
