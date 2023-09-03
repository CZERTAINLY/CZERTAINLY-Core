package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.ApprovalProfile;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalProfileRepository extends SecurityFilterRepository<ApprovalProfile, UUID> {

    Optional<ApprovalProfile> findByName(String name);


}
