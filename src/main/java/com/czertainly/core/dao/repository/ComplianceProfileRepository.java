package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.ComplianceProfile;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComplianceProfileRepository extends SecurityFilterRepository<ComplianceProfile, Long> {

    Optional<ComplianceProfile> findByUuid(String uuid);

    Optional<ComplianceProfile> findByName(String name);
}
