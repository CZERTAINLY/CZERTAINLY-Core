package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Approval;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ApprovalRepository extends SecurityFilterRepository<Approval, UUID> {

}
