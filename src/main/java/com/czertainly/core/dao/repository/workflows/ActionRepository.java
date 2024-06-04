package com.czertainly.core.dao.repository.workflows;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.workflows.Action;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActionRepository extends SecurityFilterRepository<Action, UUID> {

    boolean existsByName(String name);

    @EntityGraph(attributePaths = {"triggers"})
    Optional<Action> findWithTriggersByUuid(UUID uuid);

    List<Action> findAllByResource(Resource resource);

}
