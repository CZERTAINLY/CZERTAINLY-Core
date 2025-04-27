package com.czertainly.core.dao.repository.workflows;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.workflows.TriggerAssociation;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TriggerAssociationRepository extends SecurityFilterRepository<TriggerAssociation, UUID> {

    @EntityGraph(attributePaths = {"trigger"})
    List<TriggerAssociation> findAllByEventAndResourceAndObjectUuidOrderByTriggerOrderAsc(ResourceEvent resourceEvent, Resource resource, UUID objectUuid);

    long deleteByTriggerUuid(UUID triggerUuid);

    long deleteByResourceAndObjectUuid(Resource resource, UUID objectUuid);

}
