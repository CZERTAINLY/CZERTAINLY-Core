package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.AuditLog;
import com.querydsl.core.types.Predicate;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Transactional
public interface AuditLogRepository extends SecurityFilterRepository<AuditLog, Object>, QuerydslPredicateExecutor<AuditLog> {

    List<AuditLog> findAll(Predicate predicate, Sort sort);

    long count(Predicate predicate);
}
