package com.czertainly.core.dao.repository.workflows;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.workflows.Condition;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConditionRepository extends SecurityFilterRepository<Condition, UUID> {

    boolean existsByName(String name);

    @EntityGraph(attributePaths = {"rules"})
    Optional<Condition> findWithRulesByUuid(UUID uuid);

    @Query("SELECT c FROM Condition c WHERE c.resource = ?1 OR c.resource = ?#{T(com.czertainly.api.model.core.auth.Resource).ANY}")
    List<Condition> findAllByResource(Resource resource);

}
