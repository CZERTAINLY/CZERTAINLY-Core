package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Notification;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface NotificationRepository extends SecurityFilterRepository<Notification, UUID> {

}
