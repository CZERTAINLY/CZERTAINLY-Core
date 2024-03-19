package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.AttributeDefinitionDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeCreateRequestDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeDefinitionDetailDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeDefinitionDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeUpdateRequestDto;
import com.czertainly.api.model.client.attribute.metadata.ConnectorMetadataResponseDto;
import com.czertainly.api.model.client.attribute.metadata.GlobalMetadataCreateRequestDto;
import com.czertainly.api.model.client.attribute.metadata.GlobalMetadataDefinitionDetailDto;
import com.czertainly.api.model.client.attribute.metadata.GlobalMetadataUpdateRequestDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.CustomAttribute;
import com.czertainly.api.model.common.attribute.v2.MetadataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.properties.CustomAttributeProperties;
import com.czertainly.api.model.common.attribute.v2.properties.MetadataAttributeProperties;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.AttributeDefinition;
import com.czertainly.core.dao.repository.AttributeDefinitionRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.service.AttributeService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class AttributeServiceImpl implements AttributeService {
    private static final Logger logger = LoggerFactory.getLogger(AttributeServiceImpl.class);

    private AttributeEngine attributeEngine;
    private AttributeDefinitionRepository attributeDefinitionRepository;

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Autowired
    public void setAttributeDefinitionRepository(AttributeDefinitionRepository attributeDefinitionRepository) {
        this.attributeDefinitionRepository = attributeDefinitionRepository;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.LIST)
    public List<CustomAttributeDefinitionDto> listCustomAttributes(AttributeContentType attributeContentType) {
        logger.debug("Fetching custom attributes");

        List<AttributeDefinition> customAttributes = attributeContentType == null
                ? attributeDefinitionRepository.findByType(AttributeType.CUSTOM)
                : attributeDefinitionRepository.findByTypeAndContentType(AttributeType.CUSTOM, attributeContentType);

        return customAttributes.stream().map(AttributeDefinition::mapToCustomAttributeDefinitionDto).toList();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.LIST)
    public List<AttributeDefinitionDto> listGlobalMetadata() {
        logger.debug("Fetching global metadata");

        List<AttributeDefinition> metadataAttributes = attributeDefinitionRepository.findByTypeAndGlobal(AttributeType.META, true);
        return metadataAttributes.stream().map(AttributeDefinition::mapToGlobalMetadataDefinitionDto).toList();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.DETAIL)
    public CustomAttributeDefinitionDetailDto getCustomAttribute(UUID uuid) throws NotFoundException {
        logger.debug("Fetching custom attribute with UUID: {}", uuid.toString());
        AttributeDefinition definition = attributeDefinitionRepository.findByUuidAndType(uuid, AttributeType.CUSTOM).orElseThrow(() -> new NotFoundException(AttributeDefinition.class, uuid.toString()));
        return definition.mapToCustomAttributeDefinitionDetailDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.DETAIL)
    public GlobalMetadataDefinitionDetailDto getGlobalMetadata(UUID uuid) throws NotFoundException {
        logger.debug("Fetching global metadata for UUID: {}", uuid.toString());
        AttributeDefinition definition = attributeDefinitionRepository.findByUuidAndTypeAndGlobalTrue(uuid, AttributeType.META).orElseThrow(() -> new NotFoundException(AttributeDefinition.class, uuid.toString()));
        return definition.mapToGlobalMetadataDefinitionDetailDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.CREATE)
    public CustomAttributeDefinitionDetailDto createCustomAttribute(CustomAttributeCreateRequestDto request) throws ValidationException, AlreadyExistException, AttributeException {
        logger.debug("Create custom attribute, request: {}", request);
        if (attributeDefinitionRepository.existsByTypeAndName(AttributeType.CUSTOM, request.getName())) {
            throw new AlreadyExistException("Custom Attribute with same name already exists");
        }

        CustomAttribute attribute = new CustomAttribute();
        attribute.setType(AttributeType.CUSTOM);
        attribute.setContentType(request.getContentType());
        attribute.setName(request.getName());
        attribute.setUuid(UUID.randomUUID().toString());
        attribute.setDescription(request.getDescription());
        attribute.setContent(request.getContent());

        // Setting the attribute properties based on the information from the request
        CustomAttributeProperties properties = new CustomAttributeProperties();
        properties.setList(request.isList());
        properties.setGroup(request.getGroup());
        properties.setLabel(request.getLabel());
        properties.setVisible(request.isVisible());
        properties.setMultiSelect(request.isMultiSelect());
        properties.setReadOnly(request.isReadOnly());
        properties.setRequired(request.isRequired());
        attribute.setProperties(properties);

        return attributeEngine.updateCustomAttributeDefinition(attribute, request.getResources()).mapToCustomAttributeDefinitionDetailDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.CREATE)
    public GlobalMetadataDefinitionDetailDto createGlobalMetadata(GlobalMetadataCreateRequestDto request) throws AlreadyExistException, AttributeException {
        logger.debug("Create global metadata, request: {}", request);
        if (attributeDefinitionRepository.existsByTypeAndNameAndGlobalTrue(AttributeType.META, request.getName())) {
            throw new AlreadyExistException("Global Metadata with same name already exists");
        }

        MetadataAttribute attribute = new MetadataAttribute();
        attribute.setType(AttributeType.META);
        attribute.setContentType(request.getContentType());
        attribute.setName(request.getName());
        attribute.setUuid(UUID.randomUUID().toString());
        attribute.setDescription(request.getDescription());

        // Setting the attribute properties based on the information from the request
        MetadataAttributeProperties properties = new MetadataAttributeProperties();
        properties.setGroup(request.getGroup());
        properties.setLabel(request.getLabel());
        properties.setVisible(request.isVisible());
        properties.setGlobal(true);
        attribute.setProperties(properties);

        return attributeEngine.updateMetadataAttributeDefinition(attribute, null).mapToGlobalMetadataDefinitionDetailDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.UPDATE)
    public CustomAttributeDefinitionDetailDto editCustomAttribute(UUID uuid, CustomAttributeUpdateRequestDto request) throws NotFoundException, AttributeException {
        logger.debug("Update custom attribute with uuid: {}, request: {}", uuid.toString(), request);
        AttributeDefinition definition = attributeDefinitionRepository.findByUuidAndType(uuid, AttributeType.CUSTOM).orElseThrow(() -> new NotFoundException(AttributeDefinition.class, uuid.toString()));

        CustomAttribute attribute = (CustomAttribute) definition.getDefinition();
        attribute.setDescription(request.getDescription());
        attribute.setContent(request.getContent());
        attribute.getProperties().setList(request.isList());
        attribute.getProperties().setGroup(request.getGroup());
        attribute.getProperties().setLabel(request.getLabel());
        attribute.getProperties().setVisible(request.isVisible());
        attribute.getProperties().setMultiSelect(request.isMultiSelect());
        attribute.getProperties().setReadOnly(request.isReadOnly());
        attribute.getProperties().setRequired(request.isRequired());

        return attributeEngine.updateCustomAttributeDefinition(attribute, request.getResources()).mapToCustomAttributeDefinitionDetailDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.UPDATE)
    public GlobalMetadataDefinitionDetailDto editGlobalMetadata(UUID uuid, GlobalMetadataUpdateRequestDto request) throws NotFoundException, AttributeException {
        logger.debug("Update global metadata with uuid: {}, request: {}", uuid.toString(), request);
        AttributeDefinition definition = attributeDefinitionRepository.findByUuidAndTypeAndGlobalTrue(uuid, AttributeType.META).orElseThrow(() -> new NotFoundException(AttributeDefinition.class, uuid.toString()));

        MetadataAttribute attribute = (MetadataAttribute) definition.getDefinition();
        attribute.setDescription(request.getDescription());
        attribute.getProperties().setGroup(request.getGroup());
        attribute.getProperties().setLabel(request.getLabel());
        attribute.getProperties().setVisible(request.isVisible());

        return attributeEngine.updateMetadataAttributeDefinition(attribute, null).mapToGlobalMetadataDefinitionDetailDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.DELETE)
    public void deleteCustomAttribute(UUID uuid) throws NotFoundException {
        logger.debug("Deleting custom attribute with UUID: {}", uuid);
        attributeEngine.deleteAttributeDefinition(AttributeType.CUSTOM, uuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.DELETE)
    public void bulkDeleteCustomAttributes(List<String> attributeUuids) {
        for (String uuid : attributeUuids) {
            try {
                deleteCustomAttribute(UUID.fromString(uuid));
            } catch (NotFoundException e) {
                logger.warn("Unable to find Attribute with UUID {} to be deleted", uuid);
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.ENABLE)
    public void enableCustomAttribute(UUID uuid, boolean enable) throws NotFoundException {
        logger.debug("{} custom attribute with UUID: {}", enable ? "Enabling" : "Disabling", uuid);
        AttributeDefinition definition = attributeDefinitionRepository.findByUuidAndType(uuid, AttributeType.CUSTOM).orElseThrow(() -> new NotFoundException(AttributeDefinition.class, uuid.toString()));
        definition.setEnabled(enable);
        attributeDefinitionRepository.save(definition);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.ENABLE)
    public void bulkEnableCustomAttributes(List<String> attributeUuids, boolean enable) {
        for (String uuid : attributeUuids) {
            try {
                enableCustomAttribute(UUID.fromString(uuid), enable);
            } catch (NotFoundException e) {
                logger.warn("Unable to find Attribute with UUID {}", uuid);
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.LIST)
    public List<ConnectorMetadataResponseDto> getConnectorMetadata(Optional<String> connectorUuid) {
        List<AttributeDefinition> attributeDefinitions;
        List<ConnectorMetadataResponseDto> response = new ArrayList<>();
        if (connectorUuid.isPresent() && !connectorUuid.get().isEmpty()) {
            attributeDefinitions = attributeDefinitionRepository.findByConnectorUuidAndTypeAndGlobal(UUID.fromString(connectorUuid.get()), AttributeType.META, false);
        } else {
            attributeDefinitions = attributeDefinitionRepository.findByTypeAndGlobal(AttributeType.META, false);
        }

        for (AttributeDefinition definition : attributeDefinitions) {
            if (definition.getConnectorUuid() == null) {
                continue;
            }
            ConnectorMetadataResponseDto dto = new ConnectorMetadataResponseDto();
            dto.setName(definition.getName());
            dto.setUuid(definition.getAttributeUuid().toString());
            dto.setContentType(definition.getContentType());
            dto.setLabel(definition.getLabel());
            dto.setConnectorUuid(definition.getConnectorUuid().toString());
            response.add(dto);
        }
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.UPDATE)
    public GlobalMetadataDefinitionDetailDto promoteConnectorMetadata(UUID uuid, UUID connectorUUid) throws NotFoundException {
        AttributeDefinition definition = attributeDefinitionRepository.findByConnectorUuidAndAttributeUuid(connectorUUid, uuid).orElseThrow(() -> new NotFoundException(AttributeDefinition.class, uuid.toString()));
        definition.setGlobal(true);
        attributeDefinitionRepository.save(definition);
        return getGlobalMetadata(definition.getUuid());
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.UPDATE)
    public void demoteConnectorMetadata(UUID uuid) throws NotFoundException {
        AttributeDefinition definition = attributeDefinitionRepository.findByUuidAndTypeAndGlobalTrue(uuid, AttributeType.META).orElseThrow(() -> new NotFoundException(AttributeDefinition.class, uuid.toString()));
        definition.setGlobal(false);
        attributeDefinitionRepository.save(definition);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.UPDATE)
    public void bulkDemoteConnectorMetadata(List<String> attributeUuids) {
        for (String uuid : attributeUuids) {
            try {
                demoteConnectorMetadata(UUID.fromString(uuid));
            } catch (NotFoundException e) {
                logger.warn("Unable to find global metadata with UUID {}", uuid);
            }
        }
    }

    @Override
    public List<Resource> getResources() {
        return Resource.getCustomAttributesResources();
    }

    @Override
    // TODO: ???REMOVE AS ATTRIBUTE ENGINE REPLACEMENT??? Maybe keep in both places
    public List<BaseAttribute> getResourceAttributes(Resource resource) {
        return attributeEngine.getCustomAttributesByResource(resource);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.UPDATE)
    public void updateResources(UUID uuid, List<Resource> resources) throws NotFoundException {
        attributeEngine.updateCustomAttributeResources(uuid, resources);
    }
}
