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
    @Autowired
    NotificationService notificationService;

    @Override
    public NotificationResponseDto listNotifications(NotificationRequestDto request) throws ValidationException {
        return notificationService.listNotifications(request);
    }

    @Override
    public void deleteNotification(String uuid) throws ValidationException, NotFoundException {
        notificationService.deleteNotification(uuid);
    }

    @Override
    public NotificationDto markNotificationAsRead(String uuid) throws ValidationException, NotFoundException {
        return notificationService.markNotificationAsRead(uuid);
    }

    @Override
    public void bulkDeleteNotification(List<String> uuids) {
        notificationService.bulkDeleteNotifications(uuids);
    }

    @Override
    public NotificationResponseDto bulkMarkNotificationAsRead(List<String> uuids) {
        return notificationService.bulkMarkNotificationAsRead(uuids);
    }
}
