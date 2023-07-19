package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.ApprovalProfileRelation;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ApprovalProfileRelationRepository extends SecurityFilterRepository<ApprovalProfileRelation, UUID> {


}
