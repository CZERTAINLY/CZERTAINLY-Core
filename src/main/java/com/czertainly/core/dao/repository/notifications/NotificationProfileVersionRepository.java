package com.czertainly.core.dao.repository.notifications;

import com.czertainly.core.dao.entity.notifications.NotificationProfileVersion;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationProfileVersionRepository extends SecurityFilterRepository<NotificationProfileVersion, UUID> {

    long countByNotificationInstanceRefUuid(UUID notificationInstanceRefUuid);

    @EntityGraph(attributePaths = {"notificationProfile", "notificationInstance"})
    Optional<NotificationProfileVersion> findByNotificationProfileUuidAndVersion(UUID notificationProfileUuid, int version);

    @EntityGraph(attributePaths = {"notificationProfile", "notificationInstance"})
    Optional<NotificationProfileVersion> findTopByNotificationProfileUuidOrderByVersionDesc(UUID notificationProfileUuid);

}
