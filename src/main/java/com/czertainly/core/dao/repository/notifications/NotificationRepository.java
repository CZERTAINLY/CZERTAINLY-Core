package com.czertainly.core.dao.repository.notifications;

import com.czertainly.core.dao.entity.notifications.Notification;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface NotificationRepository extends SecurityFilterRepository<Notification, UUID> {

}
