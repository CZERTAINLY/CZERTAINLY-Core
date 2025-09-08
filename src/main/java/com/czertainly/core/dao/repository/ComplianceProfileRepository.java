package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.ComplianceProfile;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComplianceProfileRepository extends SecurityFilterRepository<ComplianceProfile, UUID> {

    Optional<ComplianceProfile> findByUuid(UUID uuid);

    Optional<ComplianceProfile> findByName(String name);
}
