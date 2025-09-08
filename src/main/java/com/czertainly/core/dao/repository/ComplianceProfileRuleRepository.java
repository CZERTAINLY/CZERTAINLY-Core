package com.czertainly.core.dao.repository;

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

    Optional<ComplianceProfileRule> findByComplianceProfileUuidAndConnectorUuidAndKindAndComplianceRuleUuid(UUID complianceProfileUuid, UUID connectorUuid, String kind, UUID providerRuleUuid);

    long deleteByComplianceProfileUuid(UUID complianceProfileUuid);

    long deleteByComplianceProfileUuidAndInternalRuleUuid(UUID complianceProfileUuid, UUID internalRuleUuid);

    long deleteByComplianceProfileUuidAndConnectorUuidAndKindAndComplianceRuleUuid(UUID complianceProfileUuid, UUID connectorUuid, String kind, UUID providerRuleUuid);

    long deleteByComplianceProfileUuidAndConnectorUuidAndKindAndComplianceGroupUuid(UUID complianceProfileUuid, UUID connectorUuid, String kind, UUID providerGroupUuid);

}
