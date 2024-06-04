package com.czertainly.core.dao.repository.workflows;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.workflows.Rule;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RuleRepository extends SecurityFilterRepository<Rule, UUID> {

    boolean existsByName(String name);

    @EntityGraph(attributePaths = {"triggers"})
    Optional<Rule> findWithTriggersByUuid(UUID uuid);

    List<Rule> findAllByResource(Resource resource);


}
