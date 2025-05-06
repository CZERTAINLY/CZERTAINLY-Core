package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.NotificationProfileVersion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationProfileVersionRepository extends SecurityFilterRepository<NotificationProfileVersion, UUID> {

    @EntityGraph(attributePaths = {"notificationProfile", "notificationInstance"})
    Optional<NotificationProfileVersion> findByNotificationProfileUuidAndVersion(UUID notificationProfileUuid, int version);


}
