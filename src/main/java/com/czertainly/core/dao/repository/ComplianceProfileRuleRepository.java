package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.ComplianceProfileRule;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComplianceProfileRuleRepository extends SecurityFilterRepository<ComplianceProfileRule, Long> {

    Optional<ComplianceProfileRule> findByUuid(UUID uuid);
    Optional<ComplianceProfileRule> findByComplianceProfileUuidAndConnectorUuidAndKindAndComplianceRuleUuid(UUID complianceProfileUuid, UUID connectorUuid, String kind, UUID providerRuleUuid);

    boolean existsByComplianceProfileUuidAndInternalRuleUuid(UUID complianceProfileUuid, UUID internalRuleUuid);
    boolean existsByComplianceProfileUuidAndConnectorUuidAndKindAndComplianceRuleUuid(UUID complianceProfileUuid, UUID connectorUuid, String kind, UUID providerRuleUuid);
    boolean existsByComplianceProfileUuidAndConnectorUuidAndKindAndComplianceGroupUuid(UUID complianceProfileUuid, UUID connectorUuid, String kind, UUID providerGroupUuid);

    long deleteByConnectorUuid(UUID connectorUuid);

    long deleteByComplianceProfileUuid(UUID complianceProfileUuid);

    long deleteByComplianceProfileUuidAndInternalRuleUuid(UUID complianceProfileUuid, UUID internalRuleUuid);

    long deleteByComplianceProfileUuidAndConnectorUuidAndKindAndComplianceRuleUuid(UUID complianceProfileUuid, UUID connectorUuid, String kind, UUID providerRuleUuid);

    long deleteByComplianceProfileUuidAndConnectorUuidAndKindAndComplianceGroupUuid(UUID complianceProfileUuid, UUID connectorUuid, String kind, UUID providerGroupUuid);

}
