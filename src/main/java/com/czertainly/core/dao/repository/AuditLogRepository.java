package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.AuditLog;
import com.querydsl.core.types.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;

@Repository
@Transactional
public interface AuditLogRepository extends SecurityFilterRepository<AuditLog, Long>, QuerydslPredicateExecutor<AuditLog> {

    List<AuditLog> findAll(Predicate predicate, Sort sort);
}
