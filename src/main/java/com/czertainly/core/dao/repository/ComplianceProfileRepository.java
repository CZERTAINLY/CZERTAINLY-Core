package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.ComplianceProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComplianceProfileRepository extends JpaRepository<ComplianceProfile, Long> {

    Optional<ComplianceProfile> findByUuid(String uuid);

    Optional<ComplianceProfile> findByName(String name);
}
