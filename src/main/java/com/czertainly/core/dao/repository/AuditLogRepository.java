package com.czertainly.core.dao.repository;

import com.querydsl.core.types.Predicate;
import com.czertainly.core.dao.entity.AuditLog;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;

@Repository
@Transactional
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, QuerydslPredicateExecutor<AuditLog> {

    List<AuditLog> findAll(Predicate predicate, Sort sort);
}
