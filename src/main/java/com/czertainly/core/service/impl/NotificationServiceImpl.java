package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.notification.NotificationDto;
import com.czertainly.api.model.client.notification.NotificationRequestDto;
import com.czertainly.api.model.client.notification.NotificationResponseDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.auth.UserDto;
import com.czertainly.core.dao.entity.Notification;
import com.czertainly.core.dao.entity.NotificationRecipient;
import com.czertainly.core.dao.repository.NotificationRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.NotificationService;
import com.czertainly.core.service.RoleManagementService;
import com.czertainly.core.service.UserManagementService;
import com.czertainly.core.util.AuthHelper;
import com.czertainly.core.util.RequestValidatorHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    UserManagementService userService;

    @Autowired
    RoleManagementService roleService;

    @Override
    public NotificationDto createNotificationForUser(String message, String detail, String userUuid, Resource target, String targetUuids) throws ValidationException {
        return createNotificationForUsers(message, detail, List.of(userUuid), target, targetUuids);
    }

    @Override
    public NotificationDto createNotificationForUsers(String message, String detail, List<String> userUuids, Resource target, String targetUuids) throws ValidationException {
        Notification notification = new Notification();
        notification.setUuid(UUID.randomUUID());
        notification.setMessage(message);
        notification.setDetail(detail);
        notification.setTargetObjectType(target);
        notification.setTargetObjectIdentification(targetUuids);

        Set<NotificationRecipient> notificationRecipients = new HashSet<>();
        for (String userUuid : userUuids) {
            NotificationRecipient notificationRecipient = new NotificationRecipient();
            notificationRecipient.setUserUuid(UUID.fromString(userUuid));
            notificationRecipient.setNotificationUuid(notification.getUuid());
            notificationRecipients.add(notificationRecipient);
        }
        notification.setNotificationRecipients(notificationRecipients);

        if (notificationRecipients.isEmpty()) {
            throw new ValidationException("Unable to create notification for no recipients.");
        }

        notificationRepository.save(notification);
        return notification.mapToDto();
    }

    @Override
    public NotificationDto createNotificationForGroup(String message, String detail, String groupUuid, Resource target, String targetUuids) throws ValidationException {
        return createNotificationForUsers(message, detail, userService.listUsers().stream().filter(u -> groupUuid.equals(u.getGroupUuid())).map(UserDto::getUuid).collect(Collectors.toList()), target, targetUuids);
    }

    @Override
    public NotificationDto createNotificationForRole(String message, String detail, String roleUuid, Resource target, String targetUuids) throws ValidationException {
        return createNotificationForUsers(message, detail, roleService.getRoleUsers(roleUuid).stream().map(UserDto::getUuid).collect(Collectors.toList()), target, targetUuids);
    }

    @Override
    public NotificationResponseDto listNotifications(NotificationRequestDto request) {
        RequestValidatorHelper.revalidatePaginationRequestDto(request);
        final Pageable pageable = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());
        final UUID loggedUserUuid = UUID.fromString(AuthHelper.getUserProfile().getUser().getUuid());

        final List<Notification> notifications = request.isUnread()
                ? notificationRepository.findByNotificationRecipients_UserUuid_AndNotificationRecipients_ReadAtIsNullOrderBySentAtDesc(loggedUserUuid, pageable)
                : notificationRepository.findByNotificationRecipients_UserUuidOrderBySentAtDesc(loggedUserUuid, pageable);
        final long totalItems = request.isUnread()
                ? notificationRepository.countByNotificationRecipients_UserUuid_AndNotificationRecipients_ReadAtIsNull(loggedUserUuid)
                : notificationRepository.countByNotificationRecipients_UserUuid(loggedUserUuid);

        final NotificationResponseDto responseDto = new NotificationResponseDto();
        responseDto.setItems(notifications.stream().map(Notification::mapToDto).toList());
        responseDto.setItemsPerPage(request.getItemsPerPage());
        responseDto.setPageNumber(request.getPageNumber());
        responseDto.setTotalItems(totalItems);
        responseDto.setTotalPages((int) Math.ceil((double) totalItems / request.getItemsPerPage()));

        return responseDto;
    }

    @Override
    public void deleteNotification(String uuid) throws NotFoundException {
        final UUID loggedUserUuid = UUID.fromString(AuthHelper.getUserProfile().getUser().getUuid());
        Notification notification = notificationRepository.findByUuid(SecuredUUID.fromString(uuid)).orElseThrow(() -> new NotFoundException(Notification.class, uuid));
        notification.getNotificationRecipients().removeIf(r -> r.getUserUuid().equals(loggedUserUuid));
        if (notification.getNotificationRecipients().isEmpty()) {
            notificationRepository.delete(notification);
        } else {
            notificationRepository.save(notification);
        }
    }

    @Override
    public NotificationDto markNotificationAsRead(String uuid) throws NotFoundException {
        final UUID loggedUserUuid = UUID.fromString(AuthHelper.getUserProfile().getUser().getUuid());
        Notification notification = notificationRepository.findByUuid(SecuredUUID.fromString(uuid)).orElseThrow(() -> new NotFoundException(Notification.class, uuid));
        for (NotificationRecipient recipient : notification.getNotificationRecipients()) {
            if (recipient.getUserUuid().equals(loggedUserUuid)) {
                if (recipient.getReadAt() == null) {
                    recipient.setReadAt(new Date());
                    notificationRepository.save(notification);
                }
                break;
            }
        }
        return notification.mapToDto();
    }
}
