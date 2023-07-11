package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.notification.NotificationDto;
import com.czertainly.api.model.client.notification.NotificationRequestDto;
import com.czertainly.api.model.client.notification.NotificationResponseDto;

import java.util.List;

public interface NotificationService {
    NotificationDto createNotificationForUser(String message, String detail, String userUuid) throws ValidationException;

    NotificationDto createNotificationForUsers(String message, String detail, List<String> userUuids) throws ValidationException;

    NotificationDto createNotificationForGroup(String message, String detail, String groupUuid) throws ValidationException;

    NotificationDto createNotificationForRole(String message, String detail, String roleUuid) throws ValidationException;

    NotificationResponseDto listNotifications(NotificationRequestDto request);

    void deleteNotification(String uuid) throws NotFoundException;

    NotificationDto markNotificationAsRead(String uuid) throws NotFoundException;
}
