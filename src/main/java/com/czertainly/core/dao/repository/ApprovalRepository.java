package com.czertainly.core.dao.repository;

import com.czertainly.api.model.client.approval.ApprovalStatusEnum;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.Approval;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ApprovalRepository extends SecurityFilterRepository<Approval, UUID> {

    Approval findByResourceAndObjectUuidAndStatus(Resource resource, UUID objectUuid, ApprovalStatusEnum status);
}
