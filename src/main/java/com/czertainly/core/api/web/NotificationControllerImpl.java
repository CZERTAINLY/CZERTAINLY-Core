package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.NotificationController;
import com.czertainly.api.model.client.notification.NotificationDto;
import com.czertainly.api.model.client.notification.NotificationRequestDto;
import com.czertainly.api.model.client.notification.NotificationResponseDto;
import com.czertainly.core.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class NotificationControllerImpl implements NotificationController {

    NotificationService notificationService;

    @Autowired
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public NotificationResponseDto listNotifications(NotificationRequestDto request) throws ValidationException {
        return notificationService.listNotifications(request);
    }

    @Override
    public void deleteNotification(String uuid) throws ValidationException, NotFoundException {
        notificationService.deleteNotification(uuid);
    }

    @Override
    public void markNotificationAsRead(String uuid) throws ValidationException, NotFoundException {
        notificationService.markNotificationAsRead(uuid);
    }

    @Override
    public void bulkDeleteNotification(List<String> uuids) {
        notificationService.bulkDeleteNotifications(uuids);
    }

    @Override
    public void bulkMarkNotificationAsRead(List<String> uuids) {
        notificationService.bulkMarkNotificationAsRead(uuids);
    }
}
