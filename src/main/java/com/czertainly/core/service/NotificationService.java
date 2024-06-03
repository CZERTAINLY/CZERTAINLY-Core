package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.notification.NotificationDto;
import com.czertainly.api.model.client.notification.NotificationRequestDto;
import com.czertainly.api.model.client.notification.NotificationResponseDto;
import com.czertainly.api.model.core.auth.Resource;

import java.util.List;

public interface NotificationService {
    NotificationDto createNotificationForUser(String message, String detail, String userUuid, Resource target, String targetUuids) throws ValidationException;

    NotificationDto createNotificationForUsers(String message, String detail, List<String> userUuids, Resource target, String targetUuids) throws ValidationException;

    NotificationDto createNotificationForGroup(String message, String detail, String groupUuid, Resource target, String targetUuids) throws ValidationException;

    NotificationDto createNotificationForRole(String message, String detail, String roleUuid, Resource target, String targetUuids) throws ValidationException;

    NotificationResponseDto listNotifications(NotificationRequestDto request);

    void deleteNotification(String uuid) throws NotFoundException;

    void markNotificationAsRead(String uuid) throws NotFoundException;

    void bulkDeleteNotifications(List<String> uuids);

    void bulkMarkNotificationAsRead(List<String> uuids);
}
