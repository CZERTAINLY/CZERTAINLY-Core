package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends SecurityFilterRepository<Notification, UUID> {

    List<Notification> findByNotificationRecipients_UserUuidOrderBySentAtDesc(UUID userUuid, Pageable pageable);

    long countByNotificationRecipients_UserUuid(UUID userUuid);

    List<Notification> findByNotificationRecipients_UserUuid_AndNotificationRecipients_ReadAtIsNullOrderBySentAtDesc(UUID userUuid, Pageable pageable);

    long countByNotificationRecipients_UserUuid_AndNotificationRecipients_ReadAtIsNull(UUID userUuid);
}
