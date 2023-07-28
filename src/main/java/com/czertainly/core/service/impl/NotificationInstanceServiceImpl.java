package com.czertainly.core.service.impl;

import com.czertainly.api.clients.NotificationInstanceApiClient;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.connector.notification.NotificationProviderInstanceDto;
import com.czertainly.api.model.connector.notification.NotificationProviderInstanceRequestDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.notification.AttributeMappingDto;
import com.czertainly.api.model.core.notification.NotificationInstanceDto;
import com.czertainly.api.model.core.notification.NotificationInstanceRequestDto;
import com.czertainly.api.model.core.notification.NotificationInstanceUpdateRequestDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.NotificationInstanceMappedAttributes;
import com.czertainly.core.dao.entity.NotificationInstanceReference;
import com.czertainly.core.dao.repository.NotificationInstanceReferenceRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.AttributeService;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.service.CredentialService;
import com.czertainly.core.service.NotificationInstanceService;
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

    @Autowired
    private NotificationInstanceReferenceRepository notificationInstanceReferenceRepository;
    @Autowired
    private ConnectorService connectorService;
    @Autowired
    private CredentialService credentialService;
    @Autowired
    private NotificationInstanceApiClient notificationInstanceApiClient;
    @Autowired
    private AttributeService attributeService;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.NOTIFICATION_INSTANCE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.NOTIFICATION_INSTANCE, action = ResourceAction.LIST)
    public List<NotificationInstanceDto> listNotificationInstances() {
        return notificationInstanceReferenceRepository.findAll()
                .stream()
                .map(NotificationInstanceReference::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.NOTIFICATION_INSTANCE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.NOTIFICATION_INSTANCE, action = ResourceAction.DETAIL)
    public NotificationInstanceDto getNotificationInstance(UUID uuid) throws ConnectorException {
        NotificationInstanceReference notificationInstanceReference = getNotificationInstanceReferenceEntity(uuid);

        return getNotificationInstanceDtoFromEntity(notificationInstanceReference);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.NOTIFICATION_INSTANCE, operation = OperationType.CREATE)
    @ExternalAuthorization(resource = Resource.NOTIFICATION_INSTANCE, action = ResourceAction.CREATE)
    public NotificationInstanceDto createNotificationInstance(NotificationInstanceRequestDto request) throws AlreadyExistException, ConnectorException {
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
        NotificationInstanceReference savedInstance = notificationInstanceReferenceRepository.save(notificationInstanceRef);
        updateMappedAttributes(savedInstance, request.getAttributeMappings());
        return notificationInstanceReferenceRepository.save(savedInstance).mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.NOTIFICATION_INSTANCE, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.NOTIFICATION_INSTANCE, action = ResourceAction.UPDATE)
    public NotificationInstanceDto editNotificationInstance(UUID uuid, NotificationInstanceUpdateRequestDto request) throws ConnectorException {
        NotificationInstanceReference notificationInstanceRef = getNotificationInstanceReferenceEntity(uuid);
        NotificationInstanceDto ref = getNotificationInstanceDtoFromEntity(notificationInstanceRef);

        Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromString(ref.getConnectorUuid()));

        saveNotificationProviderInstance(notificationInstanceRef.getNotificationInstanceUuid(),
                request,
                ref.getKind(),
                ref.getName(),
                connector);

        notificationInstanceRef.setDescription(request.getDescription());
        updateMappedAttributes(notificationInstanceRef, request.getAttributeMappings());
        return notificationInstanceReferenceRepository.save(notificationInstanceRef).mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.NOTIFICATION_INSTANCE, operation = OperationType.DELETE)
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

    private NotificationInstanceDto getNotificationInstanceDtoFromEntity(NotificationInstanceReference notificationInstanceReference) throws ConnectorException {
        NotificationInstanceDto notificationInstanceDto = notificationInstanceReference.mapToDto();

        if (notificationInstanceReference.getConnector() == null) {
            notificationInstanceDto.setConnectorName(notificationInstanceReference.getConnectorName() + " (Deleted)");
            notificationInstanceDto.setConnectorUuid("");
            logger.warn("Connector associated with the Notification: {} is not found. Unable to show details",
                    notificationInstanceReference);
            return notificationInstanceDto;
        }

        NotificationProviderInstanceDto notificationProviderInstanceDto = notificationInstanceApiClient.getNotificationInstance(
                notificationInstanceReference.getConnector().mapToDto(),
                notificationInstanceReference.getNotificationInstanceUuid().toString());

        notificationInstanceDto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(notificationProviderInstanceDto.getAttributes()));
        notificationInstanceDto.setName(notificationProviderInstanceDto.getName());
        return notificationInstanceDto;
    }

    private NotificationProviderInstanceDto saveNotificationProviderInstance(UUID uuid, NotificationInstanceUpdateRequestDto request, String kind, String name, Connector connector) throws ConnectorException {
        List<DataAttribute> attributes = connectorService.mergeAndValidateAttributes(connector.getSecuredUuid(),
                FunctionGroupCode.NOTIFICATION_PROVIDER,
                request.getAttributes(),
                kind);

        // Load complete credential data
        credentialService.loadFullCredentialData(attributes);

        NotificationProviderInstanceRequestDto notificationInstanceDto = new NotificationProviderInstanceRequestDto();
        notificationInstanceDto.setAttributes(AttributeDefinitionUtils.getClientAttributes(attributes));
        notificationInstanceDto.setKind(kind);
        notificationInstanceDto.setName(name);

        return uuid == null ? notificationInstanceApiClient.createNotificationInstance(connector.mapToDto(),
                notificationInstanceDto) : notificationInstanceApiClient.updateNotificationInstance(connector.mapToDto(),
                uuid.toString(),
                notificationInstanceDto);
    }

    private static void updateMappedAttributes(NotificationInstanceReference savedInstance, List<AttributeMappingDto> request) {
        savedInstance.setNotificationInstanceMappedAttributes(request.stream().map(attributeMappingDto -> {
            NotificationInstanceMappedAttributes mappedAttributes = new NotificationInstanceMappedAttributes();
            mappedAttributes.setNotificationInstanceRefUuid(savedInstance.getUuid());
            mappedAttributes.setAttributeDefinitionUuid(mappedAttributes.getAttributeDefinitionUuid());
            mappedAttributes.setMappingAttributeUuid(mappedAttributes.getMappingAttributeUuid());
            mappedAttributes.setMappingAttributeName(mappedAttributes.getMappingAttributeName());
            return mappedAttributes;
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
        attributeService.deleteAttributeContent(notificationInstanceRef.getUuid(), Resource.NOTIFICATION_INSTANCE);
        notificationInstanceReferenceRepository.delete(notificationInstanceRef);
    }
}
