package com.czertainly.core.service.impl;

import com.czertainly.api.clients.NotificationInstanceApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.connector.notification.NotificationProviderInstanceDto;
import com.czertainly.api.model.connector.notification.NotificationProviderInstanceRequestDto;
import com.czertainly.api.model.connector.notification.NotificationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.notification.AttributeMappingDto;
import com.czertainly.api.model.core.notification.NotificationInstanceDto;
import com.czertainly.api.model.core.notification.NotificationInstanceRequestDto;
import com.czertainly.api.model.core.notification.NotificationInstanceUpdateRequestDto;
import com.czertainly.api.model.core.settings.NotificationSettingsDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.NotificationInstanceMappedAttributes;
import com.czertainly.core.dao.entity.NotificationInstanceReference;
import com.czertainly.core.dao.repository.NotificationInstanceMappedAttributeRepository;
import com.czertainly.core.dao.repository.NotificationInstanceReferenceRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.service.CredentialService;
import com.czertainly.core.service.NotificationInstanceService;
import com.czertainly.core.service.SettingService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotificationInstanceServiceImpl implements NotificationInstanceService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationInstanceServiceImpl.class);

    private NotificationInstanceReferenceRepository notificationInstanceReferenceRepository;
    private NotificationInstanceMappedAttributeRepository notificationInstanceMappedAttributeRepository;
    private ConnectorService connectorService;
    private CredentialService credentialService;
    private NotificationInstanceApiClient notificationInstanceApiClient;
    private SettingService settingService;
    private AttributeEngine attributeEngine;

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Autowired
    public void setNotificationInstanceReferenceRepository(NotificationInstanceReferenceRepository notificationInstanceReferenceRepository) {
        this.notificationInstanceReferenceRepository = notificationInstanceReferenceRepository;
    }

    @Autowired
    public void setNotificationInstanceMappedAttributeRepository(NotificationInstanceMappedAttributeRepository notificationInstanceMappedAttributeRepository) {
        this.notificationInstanceMappedAttributeRepository = notificationInstanceMappedAttributeRepository;
    }

    @Autowired
    public void setConnectorService(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @Autowired
    public void setCredentialService(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @Autowired
    public void setNotificationInstanceApiClient(NotificationInstanceApiClient notificationInstanceApiClient) {
        this.notificationInstanceApiClient = notificationInstanceApiClient;
    }

    @Autowired
    public void setSettingService(SettingService settingService) {
        this.settingService = settingService;
    }

    @Override
    @ExternalAuthorization(resource = Resource.NOTIFICATION_INSTANCE, action = ResourceAction.LIST)
    public List<NotificationInstanceDto> listNotificationInstances() {
        return notificationInstanceReferenceRepository.findAll()
                .stream()
                .map(NotificationInstanceReference::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @ExternalAuthorization(resource = Resource.NOTIFICATION_INSTANCE, action = ResourceAction.DETAIL)
    public NotificationInstanceDto getNotificationInstance(UUID uuid) throws ConnectorException {
        NotificationInstanceReference notificationInstanceReference = getNotificationInstanceReferenceEntity(uuid);

        List<ResponseAttributeDto> attributes = attributeEngine.getObjectDataAttributesContent(notificationInstanceReference.getConnectorUuid(), null, Resource.NOTIFICATION_INSTANCE, notificationInstanceReference.getUuid());

        NotificationInstanceDto notificationInstanceDto = notificationInstanceReference.mapToDto();
        notificationInstanceDto.setAttributeMappings(notificationInstanceReference.getMappedAttributes()
                .stream()
                .map(NotificationInstanceMappedAttributes::mapToDto)
                .collect(Collectors.toList()));

        if (notificationInstanceReference.getConnector() == null) {
            notificationInstanceDto.setConnectorName(notificationInstanceReference.getConnectorName() + " (Deleted)");
            notificationInstanceDto.setConnectorUuid("");
            notificationInstanceDto.setAttributes(attributes);
            logger.warn("Connector associated with the Notification: {} is not found. Unable to show details", notificationInstanceReference.getName());
            return notificationInstanceDto;
        }

        NotificationProviderInstanceDto notificationProviderInstanceDto = notificationInstanceApiClient.getNotificationInstance(
                notificationInstanceReference.getConnector().mapToDto(),
                notificationInstanceReference.getNotificationInstanceUuid().toString());

        if (attributes.isEmpty() && notificationProviderInstanceDto.getAttributes() != null && !notificationProviderInstanceDto.getAttributes().isEmpty()) {
            try {
                List<RequestAttributeDto> requestAttributes = AttributeDefinitionUtils.getClientAttributes(notificationProviderInstanceDto.getAttributes());
                attributeEngine.updateDataAttributeDefinitions(notificationInstanceReference.getConnectorUuid(), null, notificationProviderInstanceDto.getAttributes());
                attributes = attributeEngine.updateObjectDataAttributesContent(notificationInstanceReference.getConnectorUuid(), null, Resource.NOTIFICATION_INSTANCE, notificationInstanceReference.getUuid(), requestAttributes);
            } catch (AttributeException e) {
                logger.warn("Could not update data attributes for notification instance {} retrieved from connector", notificationInstanceReference.getName());
            }
        }

        notificationInstanceDto.setAttributes(attributes);

        return notificationInstanceDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.NOTIFICATION_INSTANCE, action = ResourceAction.CREATE)
    public NotificationInstanceDto createNotificationInstance(NotificationInstanceRequestDto request) throws AlreadyExistException, ConnectorException, AttributeException {
        if (notificationInstanceReferenceRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(NotificationInstanceReference.class, request.getName());
        }

        Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromString(request.getConnectorUuid()));

        NotificationProviderInstanceDto response = saveNotificationProviderInstance(null,
                request,
                request.getKind(),
                request.getName(),
                connector);

        NotificationInstanceReference notificationInstanceRef = new NotificationInstanceReference();
        notificationInstanceRef.setNotificationInstanceUuid(UUID.fromString(response.getUuid()));
        notificationInstanceRef.setName(request.getName());
        notificationInstanceRef.setDescription(request.getDescription());
        notificationInstanceRef.setConnector(connector);
        notificationInstanceRef.setKind(request.getKind());
        notificationInstanceRef.setConnectorName(connector.getName());
        notificationInstanceRef.setConnectorUuid(connector.getUuid());
        notificationInstanceReferenceRepository.save(notificationInstanceRef);

        updateMappedAttributes(notificationInstanceRef, request.getAttributeMappings());
        notificationInstanceReferenceRepository.save(notificationInstanceRef);

        NotificationInstanceDto dto = notificationInstanceRef.mapToDto();
        dto.setAttributes(attributeEngine.updateObjectDataAttributesContent(notificationInstanceRef.getConnectorUuid(), null, Resource.NOTIFICATION_INSTANCE, notificationInstanceRef.getUuid(), request.getAttributes()));

        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.NOTIFICATION_INSTANCE, action = ResourceAction.UPDATE)
    public NotificationInstanceDto editNotificationInstance(UUID uuid, NotificationInstanceUpdateRequestDto request) throws ConnectorException, AttributeException {
        NotificationInstanceReference notificationInstanceRef = getNotificationInstanceReferenceEntity(uuid);
        Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromUUID(notificationInstanceRef.getConnectorUuid()));

        saveNotificationProviderInstance(notificationInstanceRef.getNotificationInstanceUuid(),
                request,
                notificationInstanceRef.getKind(),
                notificationInstanceRef.getName(),
                connector);

        notificationInstanceRef.setDescription(request.getDescription());

        for (NotificationInstanceMappedAttributes mappedAttribute : notificationInstanceRef.getMappedAttributes()) {
            notificationInstanceMappedAttributeRepository.delete(mappedAttribute);
        }
        notificationInstanceRef.getMappedAttributes().clear();

        updateMappedAttributes(notificationInstanceRef, request.getAttributeMappings());

        NotificationInstanceDto dto = notificationInstanceReferenceRepository.save(notificationInstanceRef).mapToDto();
        dto.setAttributes(attributeEngine.updateObjectDataAttributesContent(notificationInstanceRef.getConnectorUuid(), null, Resource.NOTIFICATION_INSTANCE, notificationInstanceRef.getUuid(), request.getAttributes()));

        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.NOTIFICATION_INSTANCE, action = ResourceAction.DELETE)
    public void deleteNotificationInstance(UUID uuid) throws ConnectorException {
        NotificationInstanceReference notificationInstanceRef = getNotificationInstanceReferenceEntity(uuid);
        removeNotificationInstance(notificationInstanceRef);
    }

    @Override
    public List<DataAttribute> listMappingAttributes(String connectorUuid, String kind) throws ConnectorException {
        Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromString(connectorUuid));
        return notificationInstanceApiClient.listMappingAttributes(connector.mapToDto(), kind);
    }

    private NotificationInstanceReference getNotificationInstanceReferenceEntity(UUID uuid) throws NotFoundException {
        return notificationInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(NotificationInstanceReference.class, uuid));
    }

    private NotificationProviderInstanceDto saveNotificationProviderInstance(UUID uuid, NotificationInstanceUpdateRequestDto request, String kind, String name, Connector connector) throws ConnectorException, AttributeException {
        connectorService.mergeAndValidateAttributes(connector.getSecuredUuid(), FunctionGroupCode.NOTIFICATION_PROVIDER, request.getAttributes(), kind);

        // Load complete credential data
        var dataAttributes = attributeEngine.getDataAttributesByContent(connector.getUuid(), request.getAttributes());
        credentialService.loadFullCredentialData(dataAttributes);

        NotificationProviderInstanceRequestDto notificationInstanceDto = new NotificationProviderInstanceRequestDto();
        notificationInstanceDto.setAttributes(AttributeDefinitionUtils.getClientAttributes(dataAttributes));
        notificationInstanceDto.setKind(kind);
        notificationInstanceDto.setName(name);

        return uuid == null ? notificationInstanceApiClient.createNotificationInstance(connector.mapToDto(),
                notificationInstanceDto) : notificationInstanceApiClient.updateNotificationInstance(connector.mapToDto(),
                uuid.toString(),
                notificationInstanceDto);
    }

    private void updateMappedAttributes(NotificationInstanceReference savedInstance, List<AttributeMappingDto> request) {
        savedInstance.setMappedAttributes(request.stream().map(attributeMappingDto -> {
            NotificationInstanceMappedAttributes mappedAttribute = new NotificationInstanceMappedAttributes();
            mappedAttribute.setNotificationInstanceRefUuid(savedInstance.getUuid());
            mappedAttribute.setAttributeDefinitionUuid(UUID.fromString(attributeMappingDto.getCustomAttributeUuid()));
            mappedAttribute.setMappingAttributeUuid(UUID.fromString(attributeMappingDto.getMappingAttributeUuid()));
            mappedAttribute.setMappingAttributeName(attributeMappingDto.getMappingAttributeName());
            return mappedAttribute;
        }).collect(Collectors.toList()));
    }

    private void removeNotificationInstance(NotificationInstanceReference notificationInstanceRef) throws ValidationException {
        if (notificationInstanceRef.getConnector() != null) {
            try {
                notificationInstanceApiClient.removeNotificationInstance(notificationInstanceRef.getConnector().mapToDto(),
                        notificationInstanceRef.getNotificationInstanceUuid().toString());
            } catch (NotFoundException notFoundException) {
                logger.warn("Notification is already deleted in the connector. Proceeding to remove it from the core");
            } catch (Exception e) {
                logger.error(e.getMessage());
                throw new ValidationException(e.getMessage());
            }
        } else {
            logger.debug("Deleting notification without connector: {}", notificationInstanceRef);
        }
        notificationInstanceReferenceRepository.delete(notificationInstanceRef);

        // check notifications settings and remove deleted instance if used
        NotificationSettingsDto notificationsSettings = settingService.getNotificationSettings();
        if (notificationsSettings != null) {
            boolean updated = false;
            for (NotificationType notificationType : NotificationType.values()) {
                String notificationInstanceUuid = notificationsSettings.getNotificationsMapping().get(notificationType);
                if (notificationInstanceUuid != null && UUID.fromString(notificationInstanceUuid).equals(notificationInstanceRef.getUuid())) {
                    updated = true;
                    notificationsSettings.getNotificationsMapping().remove(notificationType);
                }
            }

            if (updated) {
                logger.debug("Updating notifications settings. Removing deleted notification instance");
                settingService.updateNotificationSettings(notificationsSettings);
            }
        }
    }
}
