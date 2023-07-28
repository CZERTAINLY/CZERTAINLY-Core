package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.core.notification.NotificationInstanceDto;
import com.czertainly.api.model.core.notification.NotificationInstanceRequestDto;
import com.czertainly.api.model.core.notification.NotificationInstanceUpdateRequestDto;

import java.util.List;
import java.util.UUID;

public interface NotificationInstanceService {
    List<NotificationInstanceDto> listNotificationInstances();

    NotificationInstanceDto getNotificationInstance(UUID uuid) throws ConnectorException;

    NotificationInstanceDto createNotificationInstance(NotificationInstanceRequestDto request) throws AlreadyExistException, ConnectorException;

    NotificationInstanceDto editNotificationInstance(UUID uuid, NotificationInstanceUpdateRequestDto request) throws ConnectorException;

    void deleteNotificationInstance(UUID uuid) throws ConnectorException;

    List<DataAttribute> listMappingAttributes(String connectorUuid, String kind) throws ConnectorException;
}
