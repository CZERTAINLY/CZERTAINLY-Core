package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.ComplianceProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComplianceProfileRepository extends SecurityFilterRepository<ComplianceProfile, UUID> {

    @EntityGraph(attributePaths = {"complianceRules", "complianceRules.internalRule", "associations"})
    Optional<ComplianceProfile> findWithAssociationsByUuid(UUID uuid);

    Optional<ComplianceProfile> findByName(String name);

    List<ComplianceProfile> findDistinctByComplianceRulesConnectorUuid(UUID connectorUuid);

    List<ComplianceProfile> findDistinctByComplianceRulesInternalRuleUuid(UUID internalRuleUuid);
}
