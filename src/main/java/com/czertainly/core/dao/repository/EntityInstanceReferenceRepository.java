package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.EntityInstanceReference;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EntityInstanceReferenceRepository extends SecurityFilterRepository<EntityInstanceReference, Long> {

    Optional<EntityInstanceReference> findByUuid(UUID uuid);

    Optional<EntityInstanceReference> findByName(String name);
}
