package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.ComplianceInternalRule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComplianceInternalRuleRepository extends SecurityFilterRepository<ComplianceInternalRule, UUID> {

    boolean existsByName(String name);

    @EntityGraph(attributePaths = {"conditionItems"})
    Optional<ComplianceInternalRule> findByUuid(UUID uuid);

    @Override
    @EntityGraph(attributePaths = {"conditionItems"})
    List<ComplianceInternalRule> findAll();

    @EntityGraph(attributePaths = {"conditionItems"})
    List<ComplianceInternalRule> findByResource(Resource resource);

}
