package com.czertainly.core.dao.repository.notifications;

import com.czertainly.core.dao.entity.notifications.NotificationInstanceMappedAttributes;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationInstanceMappedAttributeRepository extends SecurityFilterRepository<NotificationInstanceMappedAttributes, UUID> {
}
