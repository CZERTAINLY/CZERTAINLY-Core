package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.ApprovalStep;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ApprovalStepRepository extends SecurityFilterRepository<ApprovalStep, UUID> {
}
