package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.AttributeDefinitionDto;
import com.czertainly.api.model.client.attribute.CustomAttributeCreateRequestDto;
import com.czertainly.api.model.client.attribute.CustomAttributeDefinitionDetailDto;
import com.czertainly.api.model.client.attribute.CustomAttributeUpdateRequestDto;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttributeProperties;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.AttributeContent;
import com.czertainly.core.dao.entity.AttributeContent2Object;
import com.czertainly.core.dao.entity.AttributeDefinition;
import com.czertainly.core.dao.entity.AttributeRelation;
import com.czertainly.core.dao.repository.AttributeContent2ObjectRepository;
import com.czertainly.core.dao.repository.AttributeContentRepository;
import com.czertainly.core.dao.repository.AttributeDefinitionRepository;
import com.czertainly.core.dao.repository.AttributeRelationRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AttributeService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AttributeServiceImpl implements AttributeService {
    private static final Logger logger = LoggerFactory.getLogger(AttributeServiceImpl.class);
    private final List<Resource> CUSTOM_ATTRIBUTE_COMPLIANT_RESOURCES = List.of(
            Resource.CERTIFICATE,
            Resource.CERTIFICATE_GROUP,
            Resource.RA_PROFILE,
            Resource.ACME_PROFILE,
            Resource.COMPLIANCE_PROFILE,
            Resource.DISCOVERY,
            Resource.USER,
            Resource.ROLE,
            Resource.CONNECTOR,
            Resource.CREDENTIAL,
            Resource.AUTHORITY,
            Resource.ENTITY,
            Resource.LOCATION
    );
    private AttributeDefinitionRepository attributeDefinitionRepository;
    private AttributeRelationRepository attributeRelationRepository;
    private AttributeContentRepository attributeContentRepository;
    private AttributeContent2ObjectRepository attributeContent2ObjectRepository;

    @Autowired
    public void setAttributeDefinitionRepository(AttributeDefinitionRepository attributeDefinitionRepository) {
        this.attributeDefinitionRepository = attributeDefinitionRepository;
    }

    @Autowired
    public void setAttributeRelationRepository(AttributeRelationRepository attributeRelationRepository) {
        this.attributeRelationRepository = attributeRelationRepository;
    }

    @Autowired
    public void setAttributeContentRepository(AttributeContentRepository attributeContentRepository) {
        this.attributeContentRepository = attributeContentRepository;
    }

    @Autowired
    public void setAttributeContent2ObjectRepository(AttributeContent2ObjectRepository attributeContent2ObjectRepository) {
        this.attributeContent2ObjectRepository = attributeContent2ObjectRepository;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.LIST)
    public List<AttributeDefinitionDto> listAttributes(SecurityFilter filter, AttributeType type) {
        logger.info("Fetching attributes for Type: {}", type.getCode());
        return attributeDefinitionRepository.findUsingSecurityFilterAndType(filter, AttributeType.CUSTOM).stream().map(e -> e.mapToListDto(type)).collect(Collectors.toList());
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.DETAIL)
    public CustomAttributeDefinitionDetailDto getAttribute(SecuredUUID uuid) throws NotFoundException {
        logger.info("Fetching attributes for attribute with UUID: {}", uuid.toString());
        CustomAttributeDefinitionDetailDto dto = getAttributeDefinition(uuid, AttributeType.CUSTOM).mapToCustomAttributeDefinitionDetailDto();
        dto.setResources(attributeRelationRepository.findByAttributeDefinitionUuid(uuid.getValue()).stream().map(AttributeRelation::getResource).collect(Collectors.toList()));
        logger.debug("Attribute Definition Detail: {}", dto);
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.CREATE)
    public CustomAttributeDefinitionDetailDto createAttribute(CustomAttributeCreateRequestDto request) throws ValidationException, AlreadyExistException {
        validateAttributeCreation(request, AttributeType.CUSTOM);
        return createAttributeEntity(request).mapToCustomAttributeDefinitionDetailDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.UPDATE)
    public CustomAttributeDefinitionDetailDto editAttribute(SecuredUUID uuid, CustomAttributeUpdateRequestDto request) throws NotFoundException {
        logger.info("Update attribute with uuid: {}, request: {}", uuid.toString(), request);
        CustomAttributeDefinitionDetailDto dto = editAttributeEntity(uuid, request).mapToCustomAttributeDefinitionDetailDto();
        dto.setResources(attributeRelationRepository.findByAttributeDefinitionUuid(uuid.getValue()).stream().map(AttributeRelation::getResource).collect(Collectors.toList()));
        logger.debug("Attribute Definition Updated: {}", dto);
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.DELETE)
    public void deleteAttribute(SecuredUUID uuid, AttributeType type) throws NotFoundException {
        deleteAttribute(uuid.getValue(), type);
        logger.info("Attribute with uuid {} deleted", uuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.ENABLE)
    public void enableAttribute(SecuredUUID uuid, AttributeType type) throws NotFoundException {
        enableAttribute(uuid.getValue(), type);
        logger.info("Attribute with uuid {} enabled", uuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.ENABLE)
    public void disableAttribute(SecuredUUID uuid, AttributeType type) throws NotFoundException {
        disableAttribute(uuid.getValue(), type);
        logger.info("Attribute with uuid {} disabled", uuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.DELETE)
    public void bulkDeleteAttributes(List<SecuredUUID> attributeUuids, AttributeType type) {
        for (SecuredUUID uuid : attributeUuids) {
            try {
                deleteAttribute(uuid.getValue(), type);
            } catch (NotFoundException e) {
                logger.warn("Unable to find Attribute with UUID {}", uuid);
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.ENABLE)
    public void bulkEnableAttributes(List<SecuredUUID> attributeUuids, AttributeType type) {
        for (SecuredUUID uuid : attributeUuids) {
            try {
                enableAttribute(uuid.getValue(), type);
            } catch (NotFoundException e) {
                logger.warn("Unable to find Attribute with UUID {}", uuid);
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.ENABLE)
    public void bulkDisableAttributes(List<SecuredUUID> attributeUuids, AttributeType type) {
        for (SecuredUUID uuid : attributeUuids) {
            try {
                disableAttribute(uuid.getValue(), type);
            } catch (NotFoundException e) {
                logger.warn("Unable to find Attribute with UUID {}", uuid);
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.UPDATE)
    public void updateResources(SecuredUUID uuid, List<Resource> resources) throws NotFoundException {
        AttributeDefinition attributeDefinition = getAttributeByUuid(uuid.getValue(), AttributeType.CUSTOM);
        attributeRelationRepository.deleteAll(attributeRelationRepository.findByAttributeDefinitionUuid(attributeDefinition.getUuid()));
        Set<String> differences = resources.stream().filter(e -> !CUSTOM_ATTRIBUTE_COMPLIANT_RESOURCES.contains(e)).map(Resource::getCode).collect(Collectors.toSet());
        if (differences.size() > 0) {
            throw new ValidationException(ValidationError.create("Unsupported Resources for Custom Attribute binding: " + StringUtils.join(differences, ", ")));
        }
        for (Resource resource : new HashSet<>(resources)) {
            AttributeRelation attributeRelation = new AttributeRelation();
            attributeRelation.setAttributeDefinition(attributeDefinition);
            attributeRelation.setResource(resource);
            attributeRelationRepository.save(attributeRelation);
        }
    }

    @Override
    public List<BaseAttribute> getResourceAttributes(Resource resource) {
        List<BaseAttribute> attributes = new ArrayList<>();
        for (AttributeRelation relation : attributeRelationRepository.findByResource(resource)) {
            if (relation.getAttributeDefinition().isEnabled()) {
                attributes.add(relation.getAttributeDefinition().getAttributeDefinition(DataAttribute.class));
            }
        }
        logger.debug("Attributes for the resource: {}, is : {}", resource, attributes);
        return attributes;
    }

    @Override
    public void validateCustomAttributes(List<RequestAttributeDto> attributes, Resource resource) throws ValidationException {
        logger.info("Validating custom attributes: {}", attributes);
        List<BaseAttribute> definitions = getResourceAttributes(resource);
        if (definitions.size() == 0 && attributes == null) {
            return;
        }
        if (definitions.size() == 0 && attributes.size() > 0) {
            throw new ValidationException(ValidationError.create("Custom attributes are provided for the resource that does not accept them"));
        }
        if (attributes == null) {
            attributes = new ArrayList<>();
        }
        AttributeDefinitionUtils.validateAttributes(definitions, attributes);
    }

    @Override
    public void createAttributeContent(UUID objectUuid, List<RequestAttributeDto> attributes, Resource resource) {
        if (attributes == null) {
            return;
        }
        for (RequestAttributeDto attribute : attributes) {
            logger.debug("Creating attribute with data: {}", attribute);
            createAttributeContent(objectUuid, attribute.getName(), attribute.getContent(), resource);
        }
    }

    @Override
    public void updateAttributeContent(UUID objectUuid, List<RequestAttributeDto> attributes, Resource resource) {
        logger.info("Updating the content of {} with UUID: {}", resource, objectUuid);
        deleteAttributeContent(objectUuid, attributes, resource);
        createAttributeContent(objectUuid, attributes, resource);
    }

    @Override
    public void deleteAttributeContent(UUID objectUuid, List<RequestAttributeDto> attributes, Resource resource) {
        logger.info("Deleting the content of the custom attribute for {} with UUID: {}", resource, objectUuid);
        if (attributes == null) {
            return;
        }
        List<String> oldUuids = attributes.stream().map(RequestAttributeDto::getUuid).collect(Collectors.toList());
        for (AttributeContent2Object object : attributeContent2ObjectRepository.findByObjectUuidAndObjectType(objectUuid, resource)) {
            AttributeDefinition definition = object.getAttributeContent().getAttributeDefinition();
            if (definition.getType().equals(AttributeType.CUSTOM) && oldUuids.contains(definition.getAttributeUuid().toString())) {
                attributeContent2ObjectRepository.delete(object);
                if (attributeContent2ObjectRepository.findByAttributeContent(object.getAttributeContent()).size() == 0) {
                    attributeContentRepository.delete(object.getAttributeContent());
                }
            }
        }
    }

    @Override
    public void deleteAttributeContent(UUID objectUuid, Resource resource) {
        logger.info("Deleting the attribute content for: {} with UUID: {}", resource, objectUuid);
        for (AttributeContent2Object object : attributeContent2ObjectRepository.findByObjectUuidAndObjectType(objectUuid, resource)) {
            AttributeDefinition definition = object.getAttributeContent().getAttributeDefinition();
            if (definition.getType().equals(AttributeType.CUSTOM)) {
                attributeContent2ObjectRepository.delete(object);
                if (attributeContent2ObjectRepository.findByAttributeContent(object.getAttributeContent()).size() == 0) {
                    attributeContentRepository.delete(object.getAttributeContent());
                }
            }
        }
    }

    @Override
    public List<ResponseAttributeDto> getCustomAttributesWithValues(UUID uuid, Resource resource) {
        logger.info("Getting the custom attributes for {} with UUID: {}", resource, uuid);
        List<DataAttribute> attributes = new ArrayList<>();
        for (AttributeContent2Object object : attributeContent2ObjectRepository.findByObjectUuidAndObjectType(uuid, resource)) {
            AttributeDefinition definition = object.getAttributeContent().getAttributeDefinition();
            if (definition.getType().equals(AttributeType.CUSTOM) && definition.isEnabled()) {
                DataAttribute attribute = object.getAttributeContent().getAttributeDefinition().getAttributeDefinition(DataAttribute.class);
                attribute.setContent(object.getAttributeContent().getAttributeContent(BaseAttributeContent.class));
                attributes.add(attribute);
            }
        }
        logger.debug("Attributes are: {}", attributes);
        return AttributeDefinitionUtils.getResponseAttributes(attributes);
    }

    @Override
    public List<Resource> getResources() {
        logger.info("Getting the list of available resources available for the custom attribute association");
        return CUSTOM_ATTRIBUTE_COMPLIANT_RESOURCES;
    }

    private void createAttributeContent(UUID objectUuid, String attributeName, List<BaseAttributeContent> value, Resource resource) {
        logger.info("Creating the attribute content for: {} with UUID: {}", resource, objectUuid);
        String serializedContent = AttributeDefinitionUtils.serializeAttributeContent(value);
        AttributeDefinition definition = attributeDefinitionRepository.findByTypeAndAttributeName(AttributeType.CUSTOM, attributeName).orElse(null);
        if (!definition.isEnabled()) {
            logger.warn("Attribute {} is disabled and the content will not be created");
            return;
        }
        AttributeContent existingContent = attributeContentRepository.findByAttributeContentAndAttributeDefinition(serializedContent, definition).orElse(null);

        AttributeContent2Object metadata2Object = new AttributeContent2Object();
        metadata2Object.setObjectUuid(objectUuid);
        metadata2Object.setObjectType(resource);

        if (existingContent != null) {
            logger.debug("Existing content found: {}", existingContent);
            metadata2Object.setAttributeContent(existingContent);
        } else {
            logger.debug("Creating new attribute content");
            AttributeContent content = new AttributeContent();
            content.setAttributeContent(value);
            content.setAttributeDefinition(definition);
            attributeContentRepository.save(content);
            logger.debug("Attribute Content: {}", content);
            metadata2Object.setAttributeContent(content);
        }
        attributeContent2ObjectRepository.save(metadata2Object);
    }

    private AttributeDefinition getAttributeDefinition(SecuredUUID uuid, AttributeType type) throws NotFoundException {
        return attributeDefinitionRepository.findByTypeAndAttributeUuid(type, uuid.getValue()).orElseThrow(() -> new NotFoundException(AttributeDefinition.class, uuid.toString()));
    }

    private AttributeDefinition createAttributeEntity(CustomAttributeCreateRequestDto request) {
        //New Data Attribute creation
        DataAttribute attribute = new DataAttribute();
        attribute.setType(AttributeType.CUSTOM);
        attribute.setContentType(request.getContentType());
        attribute.setName(request.getName());
        attribute.setUuid(UUID.randomUUID().toString());
        attribute.setDescription(request.getDescription());
        attribute.setContent(request.getContent());

        //Setting the attribute properties based on the information from the request
        DataAttributeProperties properties = new DataAttributeProperties();
        properties.setList(request.isList());
        properties.setGroup(request.getGroup());
        properties.setLabel(request.getLabel());
        properties.setVisible(request.isVisible());
        properties.setMultiSelect(request.isMultiSelect());
        properties.setReadOnly(request.isReadOnly());
        properties.setRequired(request.isRequired());

        attribute.setProperties(properties);

        //Creating Attribute definition data
        AttributeDefinition definition = new AttributeDefinition();
        definition.setType(AttributeType.CUSTOM);
        definition.setAttributeName(attribute.getName());
        definition.setAttributeUuid(UUID.fromString(attribute.getUuid()));
        definition.setContentType(attribute.getContentType());
        definition.setAttributeDefinition(attribute);
        definition.setUuid(attribute.getUuid());
        definition.setEnabled(false);

        attributeDefinitionRepository.save(definition);

        return definition;
    }


    private AttributeDefinition editAttributeEntity(SecuredUUID uuid, CustomAttributeUpdateRequestDto request) throws NotFoundException {
        AttributeDefinition definition = getAttributeByUuid(uuid.getValue(), AttributeType.CUSTOM);
        DataAttribute attribute = definition.getAttributeDefinition(DataAttribute.class);
        attribute.setDescription(request.getDescription());
        attribute.setContent(request.getContent());

        //Setting the attribute properties based on the information from the request
        DataAttributeProperties properties = new DataAttributeProperties();
        properties.setList(request.isList());
        properties.setGroup(request.getGroup());
        properties.setLabel(request.getLabel());
        properties.setVisible(request.isVisible());
        properties.setMultiSelect(request.isMultiSelect());
        properties.setReadOnly(request.isReadOnly());
        properties.setRequired(request.isRequired());

        attribute.setProperties(properties);

        //Update Attribute definition data
        definition.setAttributeDefinition(attribute);

        attributeDefinitionRepository.save(definition);

        return definition;
    }

    private void validateAttributeCreation(CustomAttributeCreateRequestDto request, AttributeType type) throws AlreadyExistException {
        //Check if the attribute name already exists
        logger.info("Validating {} Attributes: {}", type, request);
        if (attributeDefinitionRepository.existsByTypeAndAttributeName(type, request.getName())) {
            throw new AlreadyExistException("Custom Attribute with same name already exists");
        }

        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ValidationException(ValidationError.create("Attribute Name cannot be empty"));
        }

        if (request.getLabel() == null || request.getLabel().trim().isEmpty()) {
            throw new ValidationException(ValidationError.create("Attribute Label cannot be empty"));
        }

        if (request.getContentType() == null) {
            throw new ValidationException(ValidationError.create("Content Type is mandatory"));
        }
    }

    private AttributeDefinition getAttributeByUuid(UUID attributeUuid, AttributeType type) throws NotFoundException {
        return attributeDefinitionRepository.findByTypeAndAttributeUuid(type, attributeUuid).orElseThrow(() -> new NotFoundException(AttributeDefinition.class, attributeUuid.toString()));
    }

    private void deleteAttribute(UUID attributeUuid, AttributeType type) throws NotFoundException {
        logger.info("Deleting the attribute for: {} with UUID: {}", type, attributeUuid);
        attributeDefinitionRepository.delete(getAttributeByUuid(attributeUuid, type));
    }

    private void disableAttribute(UUID attributeUuid, AttributeType type) throws NotFoundException {
        logger.info("Disabled the attribute for: {} with UUID: {}", type, attributeUuid);
        AttributeDefinition attributeDefinition = getAttributeByUuid(attributeUuid, type);
        attributeDefinition.setEnabled(false);
        attributeDefinitionRepository.save(attributeDefinition);
    }

    private void enableAttribute(UUID attributeUuid, AttributeType type) throws NotFoundException {
        logger.info("Enable the attribute for: {} with UUID: {}", type, attributeUuid);
        AttributeDefinition attributeDefinition = getAttributeByUuid(attributeUuid, type);
        attributeDefinition.setEnabled(true);
        attributeDefinitionRepository.save(attributeDefinition);
    }
}
