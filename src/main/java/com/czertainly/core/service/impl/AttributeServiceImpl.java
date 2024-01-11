package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.AttributeDefinitionDto;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeCreateRequestDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeDefinitionDetailDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeDefinitionDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeUpdateRequestDto;
import com.czertainly.api.model.client.attribute.metadata.ConnectorMetadataResponseDto;
import com.czertainly.api.model.client.attribute.metadata.GlobalMetadataCreateRequestDto;
import com.czertainly.api.model.client.attribute.metadata.GlobalMetadataDefinitionDetailDto;
import com.czertainly.api.model.client.attribute.metadata.GlobalMetadataUpdateRequestDto;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.common.attribute.v2.*;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.common.attribute.v2.properties.CustomAttributeProperties;
import com.czertainly.api.model.common.attribute.v2.properties.MetadataAttributeProperties;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.search.SearchGroup;
import com.czertainly.core.dao.entity.AttributeContent;
import com.czertainly.core.dao.entity.AttributeContent2Object;
import com.czertainly.core.dao.entity.AttributeDefinition;
import com.czertainly.core.dao.entity.AttributeRelation;
import com.czertainly.core.dao.repository.AttributeContent2ObjectRepository;
import com.czertainly.core.dao.repository.AttributeContentRepository;
import com.czertainly.core.dao.repository.AttributeDefinitionRepository;
import com.czertainly.core.dao.repository.AttributeRelationRepository;
import com.czertainly.core.model.SearchFieldObject;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AttributeService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.SearchHelper;
import com.czertainly.core.util.converter.Sql2PredicateConverter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class AttributeServiceImpl implements AttributeService {
    private static final ObjectMapper ATTRIBUTES_OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Logger logger = LoggerFactory.getLogger(AttributeServiceImpl.class);
    private final List<Resource> CUSTOM_ATTRIBUTE_COMPLIANT_RESOURCES = List.of(
            Resource.CERTIFICATE,
            Resource.GROUP,
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
            Resource.LOCATION,
            Resource.TOKEN,
            Resource.TOKEN_PROFILE,
            Resource.CRYPTOGRAPHIC_KEY
    );

    @PersistenceContext
    private EntityManager entityManager;
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
    public List<CustomAttributeDefinitionDto> listAttributes(AttributeContentType attributeContentType) {
        logger.debug("Fetching custom attributes");

        List<AttributeDefinition> customAttributes = attributeContentType == null ? attributeDefinitionRepository.findByType(AttributeType.CUSTOM) :
                attributeDefinitionRepository.findByTypeAndContentType(AttributeType.CUSTOM, attributeContentType);

        return customAttributes.stream().map(e -> {
            CustomAttributeDefinitionDto customAttributeDefinitionDto = e.mapToCustomAttributeDefinitionDto();
            customAttributeDefinitionDto.setResources(attributeRelationRepository.findByAttributeDefinitionUuid(e.getUuid()).stream().map(AttributeRelation::getResource).collect(Collectors.toList()));
            return customAttributeDefinitionDto;
        }).collect(Collectors.toList());
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.LIST)
    public List<AttributeDefinitionDto> listGlobalMetadata(SecurityFilter filter) {
        logger.debug("Fetching global metadata");
        return attributeDefinitionRepository.findUsingSecurityFilter(filter,
                (root, cb) -> cb.and(
                        cb.equal(root.get("type"), AttributeType.META),
                        cb.equal(root.get("global"), Boolean.TRUE))
        ).stream().map(AttributeDefinition::mapToGlobalMetadataDefinitionDto).collect(Collectors.toList());
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.DETAIL)
    public CustomAttributeDefinitionDetailDto getAttribute(SecuredUUID uuid) throws NotFoundException {
        logger.debug("Fetching attributes for attribute with UUID: {}", uuid.toString());
        CustomAttributeDefinitionDetailDto dto = getAttributeDefinition(uuid, AttributeType.CUSTOM).mapToCustomAttributeDefinitionDetailDto();
        dto.setResources(attributeRelationRepository.findByAttributeDefinitionUuid(uuid.getValue()).stream().map(AttributeRelation::getResource).collect(Collectors.toList()));
        logger.debug("Attribute Definition Detail: {}", dto);
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.DETAIL)
    public GlobalMetadataDefinitionDetailDto getGlobalMetadata(SecuredUUID uuid) throws NotFoundException {
        logger.debug("Fetching global metadata for UUID: {}", uuid.toString());
        GlobalMetadataDefinitionDetailDto dto = getAttributeDefinition(uuid, AttributeType.META).mapToGlobalMetadataDefinitionDetailDto();
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
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.CREATE)
    public GlobalMetadataDefinitionDetailDto createGlobalMetadata(GlobalMetadataCreateRequestDto request) throws AlreadyExistException {
        validateGlobalMetadataCreation(request, AttributeType.META);
        return createGlobalMetadataEntity(request).mapToGlobalMetadataDefinitionDetailDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.UPDATE)
    public CustomAttributeDefinitionDetailDto editAttribute(SecuredUUID uuid, CustomAttributeUpdateRequestDto request) throws NotFoundException {
        logger.debug("Update attribute with uuid: {}, request: {}", uuid.toString(), request);
        CustomAttributeDefinitionDetailDto dto = editAttributeEntity(uuid, request).mapToCustomAttributeDefinitionDetailDto();
        dto.setResources(attributeRelationRepository.findByAttributeDefinitionUuid(uuid.getValue()).stream().map(AttributeRelation::getResource).collect(Collectors.toList()));
        logger.debug("Attribute Definition Updated: {}", dto);
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.UPDATE)
    public GlobalMetadataDefinitionDetailDto editGlobalMetadata(SecuredUUID uuid, GlobalMetadataUpdateRequestDto request) throws NotFoundException {
        logger.debug("Update global metadata with uuid: {}, request: {}", uuid.toString(), request);
        GlobalMetadataDefinitionDetailDto dto = editGlobalMetadataEntity(uuid, request).mapToGlobalMetadataDefinitionDetailDto();
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.DELETE)
    public void deleteAttribute(SecuredUUID uuid, AttributeType type) throws NotFoundException {
        deleteAttributeDefinition(uuid, type);
        logger.debug("Attribute with uuid {} deleted", uuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.ENABLE)
    public void enableAttribute(SecuredUUID uuid, AttributeType type) throws NotFoundException {
        enableAttributeDefinition(uuid, type);
        logger.debug("Attribute with uuid {} enabled", uuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.ENABLE)
    public void disableAttribute(SecuredUUID uuid, AttributeType type) throws NotFoundException {
        disableAttributeDefinition(uuid, type);
        logger.debug("Attribute with uuid {} disabled", uuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.DELETE)
    public void bulkDeleteAttributes(List<SecuredUUID> attributeUuids, AttributeType type) {
        for (SecuredUUID uuid : attributeUuids) {
            try {
                deleteAttributeDefinition(uuid, type);
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
                enableAttributeDefinition(uuid, type);
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
                disableAttributeDefinition(uuid, type);
            } catch (NotFoundException e) {
                logger.warn("Unable to find Attribute with UUID {}", uuid);
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.UPDATE)
    public void updateResources(SecuredUUID uuid, List<Resource> resources) throws NotFoundException {
        AttributeDefinition attributeDefinition = getAttributeDefinition(uuid, AttributeType.CUSTOM);
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
                attributes.add(relation.getAttributeDefinition().getAttributeDefinition(CustomAttribute.class));
            }
        }
        logger.debug("Attributes for the resource: {}, is : {}", resource, attributes);
        return attributes;
    }

    @Override
    public void validateCustomAttributes(List<RequestAttributeDto> attributes, Resource resource) throws ValidationException {
        logger.debug("Validating custom attributes: {}", attributes);
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
        logger.debug("Updating the content of {} with UUID: {}", resource, objectUuid);
        deleteAttributeContent(objectUuid, attributes, resource);
        createAttributeContent(objectUuid, attributes, resource);
    }

    @Override
    public void updateAttributeContent(UUID objectUuid, UUID attributeUuid, List<BaseAttributeContent> attributeContent, Resource resource) throws NotFoundException {
        AttributeDefinition attributeDefinition = getAttributeDefinition(SecuredUUID.fromUUID(attributeUuid), AttributeType.CUSTOM);
        CustomAttribute customAttribute = attributeDefinition.getAttributeDefinition(CustomAttribute.class);

        // validate setting of readonly attribute when it is not removal
        if (customAttribute.getProperties().isReadOnly() && attributeContent != null) {
            // content has to have same amount of items (in case of lists)
            if (attributeContent.size() != customAttribute.getContent().size()) {
                throw new ValidationException(ValidationError.create("Cannot change content of readonly attribute"));
            }

            // convert to specific attribute content class based on content type and compare each item individually for equality
            var clazz = AttributeContentType.getClass(customAttribute.getContentType());
            for (int i = 0; i < attributeContent.size(); i++) {
                BaseAttributeContent<?> attributeContentMapped = (BaseAttributeContent<?>) ATTRIBUTES_OBJECT_MAPPER.convertValue(attributeContent.get(i), clazz);
                BaseAttributeContent<?> definitionContentMapped = (BaseAttributeContent<?>) ATTRIBUTES_OBJECT_MAPPER.convertValue(customAttribute.getContent().get(i), clazz);
                if (!attributeContentMapped.equals(definitionContentMapped)) {
                    throw new ValidationException(ValidationError.create("Cannot change content of readonly attribute"));
                }
            }
        }

        // find existing content for this resource and attribute
        List<AttributeContent2Object> attributeContent2Objects = attributeContent2ObjectRepository
                .findByObjectUuidAndObjectTypeAndAttributeContentAttributeDefinitionUuid(
                        objectUuid,
                        resource,
                        attributeUuid
                );

        // if no existing content yet, just add
        if (attributeContent != null && (attributeContent2Objects == null || attributeContent2Objects.isEmpty())) {
            createAttributeContent(objectUuid, attributeDefinition.getAttributeName(), attributeContent, resource);
            return;
        }

        // remove old content and add new one
        for (AttributeContent2Object attributeContent2Object : attributeContent2Objects) {
            AttributeContent content = attributeContent2Object.getAttributeContent();
            attributeContent2ObjectRepository.delete(attributeContent2Object);
            if (attributeContent2ObjectRepository.countByAttributeContent(attributeContent2Object.getAttributeContent()) == 0) {
                attributeContentRepository.delete(content);
            }
            if (attributeContent != null) {
                createAttributeContent(objectUuid, attributeDefinition.getAttributeName(), attributeContent, resource);
            }
        }
    }

    @Override
    public void deleteAttributeContent(UUID objectUuid, List<RequestAttributeDto> attributes, Resource resource) {
        logger.debug("Deleting the content of the custom attribute for {} with UUID: {}", resource, objectUuid);
        if (attributes == null) {
            return;
        }
        List<String> oldNames = attributes.stream().map(RequestAttributeDto::getName).collect(Collectors.toList());
        for (AttributeContent2Object object : attributeContent2ObjectRepository.findByObjectUuidAndObjectType(objectUuid, resource)) {
            AttributeDefinition definition = object.getAttributeContent().getAttributeDefinition();
            if (definition.getType().equals(AttributeType.CUSTOM) && oldNames.contains(definition.getAttributeName())) {
                attributeContent2ObjectRepository.delete(object);
                if (attributeContent2ObjectRepository.findByAttributeContent(object.getAttributeContent()).size() == 0) {
                    attributeContentRepository.delete(object.getAttributeContent());
                }
            }
        }
    }

    @Override
    public void deleteAttributeContent(UUID objectUuid, Resource resource) {
        logger.debug("Deleting the attribute content for: {} with UUID: {}", resource, objectUuid);
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
    public void deleteAttributeContent(UUID objectUuid, Resource resource, UUID parentObjectUuid, Resource parentResource, AttributeType type) {
        logger.debug("Deleting the attribute content for: {} with UUID: {}, Parent UUID: {}, Parent Resource: {}", resource, objectUuid, parentObjectUuid, parentResource);
        for (AttributeContent2Object object : attributeContent2ObjectRepository.findByObjectUuidAndObjectTypeAndSourceObjectUuidAndSourceObjectType(objectUuid, resource, parentObjectUuid, parentResource)) {
            AttributeDefinition definition = object.getAttributeContent().getAttributeDefinition();
            if (definition.getType().equals(type)) {
                attributeContent2ObjectRepository.delete(object);
                if (attributeContent2ObjectRepository.findByAttributeContent(object.getAttributeContent()).size() == 0) {
                    attributeContentRepository.delete(object.getAttributeContent());
                }
            }
        }
    }

    @Override
    public List<ResponseAttributeDto> getCustomAttributesWithValues(UUID uuid, Resource resource) {
        logger.debug("Getting the custom attributes for {} with UUID: {}", resource.getCode(), uuid);
        List<CustomAttribute> attributes = new ArrayList<>();
        for (AttributeContent2Object object : attributeContent2ObjectRepository.findByObjectUuidAndObjectTypeOrderByAttributeContentAttributeDefinitionAttributeName(uuid, resource)) {
            AttributeDefinition definition = object.getAttributeContent().getAttributeDefinition();
            if (definition.getType().equals(AttributeType.CUSTOM) && definition.isEnabled()) {
                CustomAttribute attribute = object.getAttributeContent().getAttributeDefinition().getAttributeDefinition(CustomAttribute.class);
                attribute.setContent(object.getAttributeContent().getAttributeContent());
                attributes.add(attribute);
            }
        }
        logger.debug("Attributes are: {}", attributes);
        return AttributeDefinitionUtils.getResponseAttributes(attributes);
    }

    @Override
    public List<Resource> getResources() {
        logger.debug("Getting the list of available resources available for the custom attribute association");
        return CUSTOM_ATTRIBUTE_COMPLIANT_RESOURCES;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.LIST)
    public List<ConnectorMetadataResponseDto> getConnectorMetadata(Optional<String> connectorUuid) {
        List<AttributeDefinition> attributeDefinitions;
        List<ConnectorMetadataResponseDto> response = new ArrayList<>();
        if (connectorUuid.isPresent() && !connectorUuid.get().isEmpty()) {
            attributeDefinitions = attributeDefinitionRepository.findByConnectorUuidAndGlobalAndType(UUID.fromString(connectorUuid.get()), false, AttributeType.META);
            attributeDefinitions.addAll(attributeDefinitionRepository.findByConnectorUuidAndGlobalAndType(UUID.fromString(connectorUuid.get()), null, AttributeType.META));
        } else {
            attributeDefinitions = attributeDefinitionRepository.findByGlobalAndType(false, AttributeType.META);
            attributeDefinitions.addAll(attributeDefinitionRepository.findByGlobalAndType(null, AttributeType.META));
        }
        for (AttributeDefinition definition : attributeDefinitions) {
            if (definition.getAttributeName() == null || definition.getAttributeUuid() == null || definition.getConnectorUuid() == null) {
                continue;
            }
            MetadataAttribute attribute = definition.getAttributeDefinition(MetadataAttribute.class);
            String label = attribute.getProperties() != null ? attribute.getProperties().getLabel() : null;
            ConnectorMetadataResponseDto dto = new ConnectorMetadataResponseDto();
            dto.setName(definition.getAttributeName());
            dto.setUuid(definition.getAttributeUuid().toString());
            dto.setContentType(definition.getContentType());
            dto.setLabel(label);
            dto.setConnectorUuid(definition.getConnectorUuid().toString());
            response.add(dto);
        }
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.UPDATE)
    public GlobalMetadataDefinitionDetailDto promoteConnectorMetadata(UUID uuid, UUID connectorUUid) throws NotFoundException {
        AttributeDefinition definition = attributeDefinitionRepository.findByConnectorUuidAndAttributeUuid(
                connectorUUid,
                uuid
        ).orElseThrow(() -> new NotFoundException(AttributeDefinition.class, uuid.toString()));
        definition.setGlobal(true);
        attributeDefinitionRepository.save(definition);
        return getGlobalMetadata(SecuredUUID.fromUUID(definition.getUuid()));
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.UPDATE)
    public void demoteConnectorMetadata(SecuredUUID uuid) throws NotFoundException {
        AttributeDefinition definition = getAttributeDefinition(uuid, AttributeType.META);
        definition.setGlobal(false);
        attributeDefinitionRepository.save(definition);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ATTRIBUTE, action = ResourceAction.UPDATE)
    public void bulkDemoteConnectorMetadata(List<SecuredUUID> attributeUuids) {
        for (SecuredUUID uuid : attributeUuids) {
            try {
                demoteConnectorMetadata(uuid);
            } catch (NotFoundException e) {
                logger.warn("Unable to find global metadata with UUID {}", uuid);
            }
        }
    }

    @Override
    public AttributeDefinition createAttributeDefinition(UUID connectorUuid, BaseAttribute attribute) {
        //If the attribute is of any other types than data, do not do anything
        if (!attribute.getType().equals(AttributeType.DATA)) {
            return null;
        }
        //If the connector has the same attribute already available, then remove it and create a new one.
        AttributeDefinition existingDefinition = attributeDefinitionRepository.findByConnectorUuidAndAttributeUuid(connectorUuid, UUID.fromString(attribute.getUuid())).orElse(null);
        if (existingDefinition != null) {
            attributeDefinitionRepository.delete(existingDefinition);
        }
        String attributeDefinition = AttributeDefinitionUtils.serialize(attribute);
        DataAttribute dataAttribute = AttributeDefinitionUtils.deserializeSingleAttribute(attributeDefinition, DataAttribute.class);

        //Creating Attribute definition data
        AttributeDefinition definition = new AttributeDefinition();
        definition.setType(AttributeType.DATA);
        definition.setAttributeName(attribute.getName());
        definition.setAttributeUuid(UUID.fromString(attribute.getUuid()));
        definition.setContentType(dataAttribute.getContentType());
        definition.setAttributeDefinition(dataAttribute);
        definition.setUuid(attribute.getUuid());
        definition.setConnectorUuid(connectorUuid);
        definition.setEnabled(false);
        definition.setReference(true);
        definition.setGlobal(false);

        attributeDefinitionRepository.save(definition);
        return definition;
    }

    @Override
    public DataAttribute getReferenceAttribute(UUID connectorUUid, String attributeName) {
        AttributeDefinition definition = attributeDefinitionRepository.findByConnectorUuidAndAttributeNameAndReference(connectorUUid, attributeName, true).orElse(null);
        if (definition != null) {
            return definition.getAttributeDefinition(DataAttribute.class);
        }
        return null;
    }

    @Override
    public List<SearchFieldDataByGroupDto> getResourceSearchableFieldInformation(Resource resource) {
        final List<SearchFieldDataByGroupDto> searchFieldDataByGroupDtos = new ArrayList<>();
        final List<SearchFieldObject> metadataSearchFieldObject = attributeContentRepository.findDistinctAttributeContentNamesByAttrTypeAndObjType(resource, List.of(AttributeType.META));
        if (!metadataSearchFieldObject.isEmpty()) {
            searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(SearchHelper.prepareSearchForJSON(metadataSearchFieldObject), SearchGroup.META));
        }

        final List<SearchFieldObject> customAttrSearchFieldObject = attributeContentRepository.findDistinctAttributeContentNamesByAttrTypeAndObjType(resource, List.of(AttributeType.CUSTOM));
        if (!customAttrSearchFieldObject.isEmpty()) {
            searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(SearchHelper.prepareSearchForJSON(customAttrSearchFieldObject), SearchGroup.CUSTOM));
        }

        return searchFieldDataByGroupDtos;
    }

    @Override
    public List<UUID> getResourceObjectUuidsByFilters(Resource resource, SecurityFilter securityFilter, List<SearchFilterRequestDto> searchFilters) {
        List<SearchFilterRequestDto> attributesFilters;
        if (searchFilters == null || searchFilters.isEmpty() || (attributesFilters = searchFilters.stream().filter(f -> f.getSearchGroup() == SearchGroup.CUSTOM || f.getSearchGroup() == SearchGroup.META).toList()).isEmpty()) {
            return null;
        }

        final List<SearchFieldObject> searchFieldObjects = attributeContentRepository.findDistinctAttributeContentNamesByAttrTypeAndObjType(resource, List.of(AttributeType.CUSTOM, AttributeType.META));
        final Sql2PredicateConverter.CriteriaQueryDataObject criteriaQueryDataObject = Sql2PredicateConverter.prepareQueryToSearchIntoAttributes(searchFieldObjects, attributesFilters, entityManager.getCriteriaBuilder(), resource);
        return attributeContent2ObjectRepository.findUsingSecurityFilterByCustomCriteriaQuery(securityFilter, criteriaQueryDataObject.getRoot(), criteriaQueryDataObject.getCriteriaQuery(), criteriaQueryDataObject.getPredicate());
    }

    private void createAttributeContent(final UUID objectUuid, final String attributeName, final List<BaseAttributeContent> baseAttributeContentList, final Resource resource) {
        logger.debug("Creating the attribute content for: {} with UUID: {}", resource, objectUuid);
        final AttributeDefinition definition = attributeDefinitionRepository.findByTypeAndAttributeName(AttributeType.CUSTOM, attributeName).orElse(null);
        if (definition == null) {
            logger.warn("Custom attribute with name '" + attributeName + "' does not exist");
            return;
        }

        final List<ValidationError> validationErrors = new ArrayList<>();
        AttributeDefinitionUtils.validateAttributeContent(
                definition.getAttributeDefinition(CustomAttribute.class),
                baseAttributeContentList,
                validationErrors
        );
        if (!validationErrors.isEmpty()) {
            throw new ValidationException(validationErrors);
        }
        if (!definition.isEnabled()) {
            logger.warn("Attribute {} is disabled and the content will not be created");
            return;
        }


        AttributeContent existingContent = null;
        final List<AttributeContent> attributeContentList = attributeContentRepository.findByBaseAttributeContentAndAttributeDefinition(baseAttributeContentList, definition);
        for (final AttributeContent ac : attributeContentList) {
            if (ac.getAttributeContentItems().size() == baseAttributeContentList.size()) {
                existingContent = ac;
            }
        }

        final AttributeContent2Object metadata2Object = new AttributeContent2Object();
        metadata2Object.setObjectUuid(objectUuid);
        metadata2Object.setObjectType(resource);

        if (existingContent != null) {
            logger.debug("Existing content found: {}", existingContent);
            metadata2Object.setAttributeContent(existingContent);
        } else {
            logger.debug("Creating new attribute content");
            final AttributeContent content = new AttributeContent();
            content.addAttributeContent(baseAttributeContentList);
            content.setAttributeDefinition(definition);
            attributeContentRepository.save(content);
            logger.debug("Attribute Content: {}", content);
            metadata2Object.setAttributeContent(content);
        }
        attributeContent2ObjectRepository.save(metadata2Object);
    }

    private AttributeDefinition getAttributeDefinition(SecuredUUID uuid, AttributeType type) throws NotFoundException {
        return attributeDefinitionRepository.findByUuid(uuid, (root, cb) -> cb.equal(root.get("type"), type)).orElseThrow(() -> new NotFoundException(AttributeDefinition.class, uuid.toString()));
    }

    private AttributeDefinition createAttributeEntity(CustomAttributeCreateRequestDto request) {
        //New Data Attribute creation
        CustomAttribute attribute = new CustomAttribute();
        attribute.setType(AttributeType.CUSTOM);
        attribute.setContentType(request.getContentType());
        attribute.setName(request.getName());
        attribute.setUuid(UUID.randomUUID().toString());
        attribute.setDescription(request.getDescription());
        attribute.setContent(request.getContent());

        //Setting the attribute properties based on the information from the request
        CustomAttributeProperties properties = new CustomAttributeProperties();
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
        // Default state of the attribute will always be enabled
        definition.setEnabled(true);

        attributeDefinitionRepository.save(definition);
        try {
            if (request.getResources() != null && !request.getResources().isEmpty()) {
                updateResources(definition.getSecuredUuid(), request.getResources());
            }
        } catch (NotFoundException e) {
            // This method will not throw error since the object will always be created before the resource assignment
            logger.error(e.getMessage());
        }
        return definition;
    }

    private AttributeDefinition createGlobalMetadataEntity(GlobalMetadataCreateRequestDto request) {
        //New Data Attribute creation
        MetadataAttribute attribute = new MetadataAttribute();
        attribute.setType(AttributeType.META);
        attribute.setContentType(request.getContentType());
        attribute.setName(request.getName());
        attribute.setUuid(UUID.randomUUID().toString());
        attribute.setDescription(request.getDescription());

        //Setting the attribute properties based on the information from the request
        MetadataAttributeProperties properties = new MetadataAttributeProperties();
        properties.setGroup(request.getGroup());
        properties.setLabel(request.getLabel());
        properties.setVisible(request.isVisible());
        properties.setGlobal(true);

        attribute.setProperties(properties);

        //Creating Attribute definition data
        AttributeDefinition definition = new AttributeDefinition();
        definition.setType(AttributeType.META);
        definition.setAttributeName(attribute.getName());
        definition.setAttributeUuid(UUID.fromString(attribute.getUuid()));
        definition.setContentType(attribute.getContentType());
        definition.setAttributeDefinition(attribute);
        definition.setUuid(attribute.getUuid());
        definition.setGlobal(true);

        attributeDefinitionRepository.save(definition);

        return definition;
    }


    private AttributeDefinition editAttributeEntity(SecuredUUID uuid, CustomAttributeUpdateRequestDto request) throws NotFoundException {
        AttributeDefinition definition = getAttributeDefinition(uuid, AttributeType.CUSTOM);
        CustomAttribute attribute = definition.getAttributeDefinition(CustomAttribute.class);
        attribute.setDescription(request.getDescription());
        attribute.setContent(request.getContent());

        //Setting the attribute properties based on the information from the request
        CustomAttributeProperties properties = new CustomAttributeProperties();
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

        updateResources(uuid, request.getResources());

        return definition;
    }

    private AttributeDefinition editGlobalMetadataEntity(SecuredUUID uuid, GlobalMetadataUpdateRequestDto request) throws NotFoundException {
        AttributeDefinition definition = getAttributeDefinition(uuid, AttributeType.META);
        MetadataAttribute attribute = definition.getAttributeDefinition(MetadataAttribute.class);
        attribute.setDescription(request.getDescription());

        //Setting the attribute properties based on the information from the request
        MetadataAttributeProperties properties = new MetadataAttributeProperties();
        properties.setGroup(request.getGroup());
        properties.setLabel(request.getLabel());
        properties.setVisible(request.isVisible());

        attribute.setProperties(properties);

        //Update Attribute definition data
        definition.setAttributeDefinition(attribute);

        attributeDefinitionRepository.save(definition);

        return definition;
    }

    private void validateAttributeCreation(CustomAttributeCreateRequestDto request, AttributeType type) throws AlreadyExistException {
        //Check if the attribute name already exists
        logger.debug("Validating {} Attributes: {}", type, request);
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

    private void validateGlobalMetadataCreation(GlobalMetadataCreateRequestDto request, AttributeType type) throws AlreadyExistException {
        //Check if the Global Metadata name already exists
        logger.debug("Validating {} Attributes: {}", type, request);
        if (attributeDefinitionRepository.existsByTypeAndAttributeName(type, request.getName())) {
            throw new AlreadyExistException("Global Metadata with same name already exists");
        }

        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ValidationException(ValidationError.create("Global Metadata Name cannot be empty"));
        }

        if (request.getLabel() == null || request.getLabel().trim().isEmpty()) {
            throw new ValidationException(ValidationError.create("Global Metadata Label cannot be empty"));
        }

        if (request.getContentType() == null) {
            throw new ValidationException(ValidationError.create("Global Metadata Type is mandatory"));
        }
    }

    private void deleteAttributeDefinition(SecuredUUID attributeUuid, AttributeType type) throws NotFoundException {
        logger.debug("Deleting the attribute for: {} with UUID: {}", type, attributeUuid);
        attributeDefinitionRepository.delete(getAttributeDefinition(attributeUuid, type));
    }

    private void disableAttributeDefinition(SecuredUUID attributeUuid, AttributeType type) throws NotFoundException {
        logger.debug("Disabled the attribute for: {} with UUID: {}", type, attributeUuid);
        AttributeDefinition attributeDefinition = getAttributeDefinition(attributeUuid, type);
        attributeDefinition.setEnabled(false);
        attributeDefinitionRepository.save(attributeDefinition);
    }

    private void enableAttributeDefinition(SecuredUUID attributeUuid, AttributeType type) throws NotFoundException {
        logger.debug("Enable the attribute for: {} with UUID: {}", type, attributeUuid);
        AttributeDefinition attributeDefinition = getAttributeDefinition(attributeUuid, type);
        attributeDefinition.setEnabled(true);
        attributeDefinitionRepository.save(attributeDefinition);
    }
}
