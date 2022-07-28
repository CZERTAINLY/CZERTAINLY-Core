package com.czertainly.core.service.impl;

import com.czertainly.api.clients.EntityInstanceApiClient;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.entity.EntityInstanceUpdateRequestDto;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.common.attribute.RequestAttributeDto;
import com.czertainly.api.model.connector.entity.EntityInstanceRequestDto;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.entity.EntityInstanceDto;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.EntityInstanceReference;
import com.czertainly.core.dao.repository.EntityInstanceReferenceRepository;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.service.CredentialService;
import com.czertainly.core.service.EntityInstanceService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class EntityInstanceServiceImpl implements EntityInstanceService {

    private static final Logger logger = LoggerFactory.getLogger(EntityInstanceServiceImpl.class);
    private EntityInstanceReferenceRepository entityInstanceReferenceRepository;
    private ConnectorService connectorService;
    private CredentialService credentialService;
    private EntityInstanceApiClient entityInstanceApiClient;

    @Autowired
    public void setEntityInstanceReferenceRepository(EntityInstanceReferenceRepository entityInstanceReferenceRepository) {
        this.entityInstanceReferenceRepository = entityInstanceReferenceRepository;
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
    public void setEntityInstanceApiClient(EntityInstanceApiClient entityInstanceApiClient) {
        this.entityInstanceApiClient = entityInstanceApiClient;
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.REQUEST)
    public List<EntityInstanceDto> listEntityInstances() {
        return entityInstanceReferenceRepository.findAll().stream().map(EntityInstanceReference::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.REQUEST)
    public EntityInstanceDto getEntityInstance(String entityUuid) throws ConnectorException {
        EntityInstanceReference entityInstanceReference = entityInstanceReferenceRepository.findByUuid(entityUuid)
                .orElseThrow(() -> new NotFoundException(EntityInstanceReference.class, entityUuid));

        if (entityInstanceReference.getConnector() == null) {
            throw new NotFoundException("Connector associated with the Entity is not found. Unable to load details");
        }

        com.czertainly.api.model.connector.entity.EntityInstanceDto entityProviderInstanceDto = entityInstanceApiClient.getEntityInstance(entityInstanceReference.getConnector().mapToDto(),
                entityInstanceReference.getEntityInstanceUuid());

        EntityInstanceDto entityInstanceDto = new EntityInstanceDto();
        entityInstanceDto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(entityProviderInstanceDto.getAttributes()));
        entityInstanceDto.setName(entityProviderInstanceDto.getName());
        entityInstanceDto.setUuid(entityInstanceReference.getUuid());
        entityInstanceDto.setConnectorUuid(entityInstanceReference.getConnector().getUuid());
        entityInstanceDto.setKind(entityInstanceReference.getKind());
        entityInstanceDto.setConnectorName(entityInstanceReference.getConnectorName());

        return entityInstanceDto;
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.CREATE)
    public EntityInstanceDto createEntityInstance(com.czertainly.api.model.client.entity.EntityInstanceRequestDto request) throws AlreadyExistException, ConnectorException {
        if (entityInstanceReferenceRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(EntityInstanceReference.class, request.getName());
        }

        Connector connector = connectorService.getConnectorEntity(request.getConnectorUuid());

        FunctionGroupCode codeToSearch = FunctionGroupCode.ENTITY_PROVIDER;

        List<AttributeDefinition> attributes = connectorService.mergeAndValidateAttributes(connector.getUuid(), codeToSearch,
                request.getAttributes(), request.getKind());

        // Load complete credential data
        credentialService.loadFullCredentialData(attributes);

        EntityInstanceRequestDto entityInstanceDto = new EntityInstanceRequestDto();
        entityInstanceDto.setAttributes(AttributeDefinitionUtils.getClientAttributes(attributes));
        entityInstanceDto.setKind(request.getKind());
        entityInstanceDto.setName(request.getName());

        com.czertainly.api.model.connector.entity.EntityInstanceDto response = entityInstanceApiClient.createEntityInstance(connector.mapToDto(), entityInstanceDto);

        EntityInstanceReference entityInstanceRef = new EntityInstanceReference();
        entityInstanceRef.setEntityInstanceUuid((response.getUuid()));
        entityInstanceRef.setName(request.getName());
        //entityInstanceRef.setStatus("connected"); // TODO: status of the Entity
        entityInstanceRef.setConnector(connector);
        entityInstanceRef.setKind(request.getKind());
        entityInstanceRef.setConnectorName(connector.getName());
        entityInstanceReferenceRepository.save(entityInstanceRef);

        logger.info("Entity {} created with Kind {}", entityInstanceRef.getUuid(), entityInstanceRef.getKind());

        return entityInstanceRef.mapToDto();
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.CHANGE)
    public EntityInstanceDto editEntityInstance(String entityUuid, EntityInstanceUpdateRequestDto request) throws ConnectorException {
        EntityInstanceReference entityInstanceRef = entityInstanceReferenceRepository.findByUuid(entityUuid)
                .orElseThrow(() -> new NotFoundException(EntityInstanceReference.class, entityUuid));

        EntityInstanceDto ref = getEntityInstance(entityUuid);
        Connector connector = connectorService.getConnectorEntity(ref.getConnectorUuid());

        FunctionGroupCode codeToSearch = FunctionGroupCode.ENTITY_PROVIDER;

        List<AttributeDefinition> attributes = connectorService.mergeAndValidateAttributes(connector.getUuid(), codeToSearch,
                request.getAttributes(), ref.getKind());

        // Load complete credential data
        credentialService.loadFullCredentialData(attributes);

        EntityInstanceRequestDto entityInstanceDto = new EntityInstanceRequestDto();
        entityInstanceDto.setAttributes(AttributeDefinitionUtils.getClientAttributes(attributes));
        entityInstanceApiClient.updateEntityInstance(connector.mapToDto(),
                entityInstanceRef.getEntityInstanceUuid(), entityInstanceDto);
        entityInstanceReferenceRepository.save(entityInstanceRef);

        logger.info("Entity {} with Kind {} updated", entityInstanceRef.getUuid(), entityInstanceRef.getKind());

        return entityInstanceRef.mapToDto();
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.DELETE)
    public void deleteEntityInstance(String entityUuid) throws ConnectorException {
        EntityInstanceReference entityInstanceRef = entityInstanceReferenceRepository.findByUuid(entityUuid)
                .orElseThrow(() -> new NotFoundException(EntityInstanceReference.class, entityUuid));

        List<ValidationError> errors = new ArrayList<>();
        if (!entityInstanceRef.getLocations().isEmpty()) {
            errors.add(ValidationError.create("Entity instance {} has {} dependent Locations", entityInstanceRef.getName(),
                    entityInstanceRef.getLocations().size()));
            entityInstanceRef.getLocations().forEach(c -> errors.add(ValidationError.create(c.getName())));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Could not delete Entity instance", errors);
        }

        entityInstanceApiClient.removeEntityInstance(entityInstanceRef.getConnector().mapToDto(), entityInstanceRef.getEntityInstanceUuid());

        entityInstanceReferenceRepository.delete(entityInstanceRef);

        logger.info("Entity instance {} was deleted", entityInstanceRef.getName());
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    public List<AttributeDefinition> listLocationAttributes(String entityUuid) throws ConnectorException {
        EntityInstanceReference entityInstance = entityInstanceReferenceRepository.findByUuid(entityUuid)
                .orElseThrow(() -> new NotFoundException(EntityInstanceReference.class, entityUuid));

        Connector connector = entityInstance.getConnector();

        return entityInstanceApiClient.listLocationAttributes(connector.mapToDto(), entityInstance.getEntityInstanceUuid());
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.VALIDATE)
    public void validateLocationAttributes(String entityUuid, List<RequestAttributeDto> attributes) throws ConnectorException {
        EntityInstanceReference entityInstance = entityInstanceReferenceRepository.findByUuid(entityUuid)
                .orElseThrow(() -> new NotFoundException(EntityInstanceReference.class, entityUuid));

        Connector connector = entityInstance.getConnector();

        entityInstanceApiClient.validateLocationAttributes(connector.mapToDto(), entityInstance.getEntityInstanceUuid(),
                attributes);
    }
}
