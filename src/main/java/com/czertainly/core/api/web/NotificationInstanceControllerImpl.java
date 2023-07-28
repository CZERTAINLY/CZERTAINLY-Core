package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.NotificationInstanceController;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.notification.NotificationInstanceDto;
import com.czertainly.api.model.core.notification.NotificationInstanceRequestDto;
import com.czertainly.api.model.core.notification.NotificationInstanceUpdateRequestDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.service.NotificationInstanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
public class NotificationInstanceControllerImpl implements NotificationInstanceController {

    private NotificationInstanceService notificationInstanceService;

    @Override
    @AuthEndpoint(resourceName = Resource.NOTIFICATION)
    public List<NotificationInstanceDto> listNotificationInstances() {
        return notificationInstanceService.listNotificationInstances();
    }

    @Override
    public NotificationInstanceDto getNotificationInstance(@PathVariable String uuid) throws NotFoundException, ConnectorException {
        return notificationInstanceService.getNotificationInstance(UUID.fromString(uuid));
    }

    @Override
    public ResponseEntity<?> createNotificationInstance(
            @RequestBody NotificationInstanceRequestDto request) throws AlreadyExistException, NotFoundException, ConnectorException {
        NotificationInstanceDto notificationInstance = notificationInstanceService.createNotificationInstance(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(notificationInstance.getUuid())
                .toUri();
        UuidDto dto = new UuidDto();
        dto.setUuid(notificationInstance.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    public NotificationInstanceDto editNotificationInstance(
            @PathVariable String uuid,
            @RequestBody NotificationInstanceUpdateRequestDto request) throws NotFoundException, ConnectorException {
        return notificationInstanceService.editNotificationInstance(UUID.fromString(uuid), request);
    }

    @Override
    public void deleteNotificationInstance(@PathVariable String uuid) throws NotFoundException, ConnectorException {
        notificationInstanceService.deleteNotificationInstance(UUID.fromString(uuid));
    }

    @Override
    public List<DataAttribute> listMappingAttributes(String connectorUuid, String kind) throws ConnectorException {
        return notificationInstanceService.listMappingAttributes(connectorUuid, kind);
    }

    @Autowired
    public void setNotificationInstanceService(NotificationInstanceService notificationInstanceService) {
        this.notificationInstanceService = notificationInstanceService;
    }

}
