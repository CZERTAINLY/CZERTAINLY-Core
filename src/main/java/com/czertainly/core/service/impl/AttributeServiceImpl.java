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
        return attributeDefinitionRepository.findUsingSecurityFilterAndType(filter, AttributeType.CUSTOM).stream().map(e -> e.mapToListDto(type)).collect(Collectors.toList());
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.DETAIL)
    public CustomAttributeDefinitionDetailDto getAttribute(SecuredUUID uuid) throws NotFoundException {
        CustomAttributeDefinitionDetailDto dto = getAttributeDefinition(uuid, AttributeType.CUSTOM).mapToCustomAttributeDefinitionDetailDto();
        dto.setResources(attributeRelationRepository.findByAttributeDefinitionUuid(uuid.getValue()).stream().map(AttributeRelation::getResource).collect(Collectors.toList()));
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
        CustomAttributeDefinitionDetailDto dto = editAttributeEntity(uuid, request).mapToCustomAttributeDefinitionDetailDto();
        dto.setResources(attributeRelationRepository.findByAttributeDefinitionUuid(uuid.getValue()).stream().map(AttributeRelation::getResource).collect(Collectors.toList()));
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.DELETE)
    public void deleteAttribute(SecuredUUID uuid, AttributeType type) throws NotFoundException {
        deleteAttribute(uuid.getValue(), type);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.ENABLE)
    public void enableAttribute(SecuredUUID uuid, AttributeType type) throws NotFoundException {
        enableAttribute(uuid.getValue(), type);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.ENABLE)
    public void disableAttribute(SecuredUUID uuid, AttributeType type) throws NotFoundException {
        disableAttribute(uuid.getValue(), type);
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
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.ANY)
    public List<BaseAttribute> getResourceAttributes(Resource resource) {
        List<BaseAttribute> attributes = new ArrayList<>();
        for (AttributeRelation relation : attributeRelationRepository.findByResource(resource)) {
            if (relation.getAttributeDefinition().isEnabled()) {
                attributes.add(relation.getAttributeDefinition().getAttributeDefinition(DataAttribute.class));
            }
        }
        return attributes;
    }

    @Override
    public void validateCustomAttributes(List<RequestAttributeDto> attributes, Resource resource) throws ValidationException {
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
            createAttributeContent(objectUuid, attribute.getName(), attribute.getContent(), resource);
        }
    }

    @Override
    public void updateAttributeContent(UUID objectUuid, List<RequestAttributeDto> attributes, Resource resource) {
        deleteAttributeContent(objectUuid, attributes, resource);
        createAttributeContent(objectUuid, attributes, resource);
    }

    @Override
    public void deleteAttributeContent(UUID objectUuid, List<RequestAttributeDto> attributes, Resource resource) {
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
        List<DataAttribute> attributes = new ArrayList<>();
        for (AttributeContent2Object object : attributeContent2ObjectRepository.findByObjectUuidAndObjectType(uuid, resource)) {
            AttributeDefinition definition = object.getAttributeContent().getAttributeDefinition();
            if (definition.getType().equals(AttributeType.CUSTOM) && definition.isEnabled()) {
                DataAttribute attribute = object.getAttributeContent().getAttributeDefinition().getAttributeDefinition(DataAttribute.class);
                attribute.setContent(object.getAttributeContent().getAttributeContent(BaseAttributeContent.class));
                attributes.add(attribute);
            }
        }
        return AttributeDefinitionUtils.getResponseAttributes(attributes);
    }

    @Override
    public List<Resource> getResources() {
        return CUSTOM_ATTRIBUTE_COMPLIANT_RESOURCES;
    }

    private void createAttributeContent(UUID objectUuid, String attributeName, List<BaseAttributeContent> value, Resource resource) {
        String serializedContent = AttributeDefinitionUtils.serializeAttributeContent(value);
        AttributeDefinition definition = attributeDefinitionRepository.findByTypeAndAttributeName(AttributeType.CUSTOM, attributeName).orElse(null);
        if (!definition.isEnabled()) {
            return;
        }
        AttributeContent existingContent = attributeContentRepository.findByAttributeContentAndAttributeDefinition(serializedContent, definition).orElse(null);

        AttributeContent2Object metadata2Object = new AttributeContent2Object();
        metadata2Object.setObjectUuid(objectUuid);
        metadata2Object.setObjectType(resource);

        if (existingContent != null) {
            metadata2Object.setAttributeContent(existingContent);
        } else {
            AttributeContent content = new AttributeContent();
            content.setAttributeContent(value);
            content.setAttributeDefinition(definition);
            attributeContentRepository.save(content);
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
        attributeDefinitionRepository.delete(getAttributeByUuid(attributeUuid, type));
    }

    private void disableAttribute(UUID attributeUuid, AttributeType type) throws NotFoundException {
        AttributeDefinition attributeDefinition = getAttributeByUuid(attributeUuid, type);
        attributeDefinition.setEnabled(false);
        attributeDefinitionRepository.save(attributeDefinition);
    }

    private void enableAttribute(UUID attributeUuid, AttributeType type) throws NotFoundException {
        AttributeDefinition attributeDefinition = getAttributeByUuid(attributeUuid, type);
        attributeDefinition.setEnabled(true);
        attributeDefinitionRepository.save(attributeDefinition);
    }
}
