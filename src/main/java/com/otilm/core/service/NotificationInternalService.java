package com.otilm.core.service;

import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.client.notification.NotificationDto;
import com.otilm.api.model.core.auth.Resource;

import java.util.List;

/**
 * A group or role with no members is ordinary configuration, so the create methods report "nobody to notify" by
 * returning {@code null} rather than by throwing. Callers run inside a shared transaction, and an exception raised
 * here would mark that transaction rollback-only past any catch of theirs, discarding the notifications already
 * created for the other recipients of the same event.
 */
public interface NotificationInternalService {

    /**
     * @return the created notification, never {@code null} -- the user is named explicitly.
     */
    NotificationDto createNotificationForUser(String message, String detail, String userUuid, Resource target, String targetUuids) throws ValidationException;

    /**
     * @return the created notification, or {@code null} when {@code userUuids} is null or empty.
     */
    NotificationDto createNotificationForUsers(String message, String detail, List<String> userUuids, Resource target, String targetUuids) throws ValidationException;

    /**
     * @return the created notification, or {@code null} when the group has no members.
     */
    NotificationDto createNotificationForGroup(String message, String detail, String groupUuid, Resource target, String targetUuids) throws ValidationException;

    /**
     * @return the created notification, or {@code null} when the role has no members.
     */
    NotificationDto createNotificationForRole(String message, String detail, String roleUuid, Resource target, String targetUuids) throws ValidationException;
}
