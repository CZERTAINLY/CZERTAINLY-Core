package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.ComplianceProfile;
import com.czertainly.core.dao.entity.ComplianceProfileRule;
import com.czertainly.core.dao.entity.ComplianceRule;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComplianceProfileRuleRepository extends SecurityFilterRepository<ComplianceProfileRule, Long> {

    Optional<ComplianceProfileRule> findByUuid(UUID uuid);

    Optional<ComplianceProfileRule> findByComplianceProfileAndComplianceRule(ComplianceProfile complianceProfile, ComplianceRule complianceRule);

    List<ComplianceProfileRule> findByUuidIn(List<UUID> uuid);

    List<ComplianceProfileRule> findByComplianceProfileUuidAndConnectorUuidAndKindAndInternalRuleUuidNull(UUID complianceProfileUuid, UUID connectorUuid, String kind);

    long deleteByComplianceProfileUuidAndInternalRuleUuidNotNull(UUID complianceProfileUuid);

}
