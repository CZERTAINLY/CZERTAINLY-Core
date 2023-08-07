package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.NotificationInstanceReference;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationInstanceReferenceRepository extends SecurityFilterRepository<NotificationInstanceReference, UUID> {

    Optional<NotificationInstanceReference> findByUuid(UUID uuid);

    Optional<NotificationInstanceReference> findByName(String name);
}
