package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.ApprovalRecipient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ApprovalRecipientRepository extends SecurityFilterRepository<ApprovalRecipient, UUID> {

}
