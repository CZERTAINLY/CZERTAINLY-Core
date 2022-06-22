package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.ComplianceProfile;
import com.czertainly.core.dao.entity.ComplianceProfileRule;
import com.czertainly.core.dao.entity.ComplianceRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComplianceProfileRuleRepository extends JpaRepository<ComplianceProfileRule, Long> {

    Optional<ComplianceProfileRule> findByUuid(String uuid);

    Optional<ComplianceProfileRule> findByComplianceProfileAndComplianceRule(ComplianceProfile complianceProfile, ComplianceRule complianceRule);
}
