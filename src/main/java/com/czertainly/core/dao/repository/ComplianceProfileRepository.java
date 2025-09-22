package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.ComplianceProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComplianceProfileRepository extends SecurityFilterRepository<ComplianceProfile, UUID> {

    @EntityGraph(attributePaths = {"complianceRules", "complianceRules.internalRule", "associations"})
    Optional<ComplianceProfile> findWithAssociationsByUuid(UUID uuid);

    @EntityGraph(attributePaths = {"complianceRules", "complianceRules.internalRule", "associations"})
    List<ComplianceProfile> findWithAssociationsByUuidIn(List<UUID> uuids);

    Optional<ComplianceProfile> findByName(String name);

    List<ComplianceProfile> findDistinctByComplianceRulesConnectorUuid(UUID connectorUuid);

    long countByComplianceRulesInternalRuleUuid(UUID internalRuleUuid);

    @Query("SELECT DISTINCT cp.name FROM ComplianceProfile cp JOIN cp.complianceRules r WHERE r.internalRule.uuid = :internalRuleUuid")
    List<String> findNamesByComplianceRulesInternalRuleUuid(@Param("internalRuleUuid") UUID internalRuleUuid);

}
