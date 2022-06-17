package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.ComplianceProfile;
import com.czertainly.core.dao.entity.Connector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public interface ComplianceProfileRepository extends JpaRepository<ComplianceProfile, Long> {

    Optional<ComplianceProfile> findByUuid(String uuid);

    Optional<ComplianceProfile> findByName(String name);
}
