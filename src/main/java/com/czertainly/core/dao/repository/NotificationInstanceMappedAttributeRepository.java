package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.NotificationInstanceMappedAttributes;
import com.czertainly.core.dao.entity.NotificationInstanceReference;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationInstanceMappedAttributeRepository extends SecurityFilterRepository<NotificationInstanceMappedAttributes, UUID> {
}
