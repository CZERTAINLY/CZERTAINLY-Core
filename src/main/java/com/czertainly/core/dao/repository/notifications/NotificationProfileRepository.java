package com.czertainly.core.dao.repository.notifications;

import com.czertainly.core.dao.entity.notifications.NotificationProfile;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationProfileRepository extends SecurityFilterRepository<NotificationProfile, UUID> {

    Optional<NotificationProfile> findByName(String name);

    @EntityGraph(attributePaths = {"versions"})
    Optional<NotificationProfile> findWithVersionsByUuid(UUID uuid);


}
