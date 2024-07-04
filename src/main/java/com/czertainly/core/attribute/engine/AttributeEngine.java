package com.czertainly.core.attribute.engine;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.metadata.MetadataResponseDto;
import com.czertainly.api.model.client.metadata.ResponseMetadataDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.*;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.core.attribute.engine.records.ObjectAttributeContent;
import com.czertainly.core.attribute.engine.records.ObjectAttributeContentDetail;
import com.czertainly.core.attribute.engine.records.ObjectAttributeContentInfo;
import com.czertainly.core.attribute.engine.records.ObjectAttributeDefinitionContent;
import com.czertainly.core.dao.entity.AttributeContent2Object;
import com.czertainly.core.dao.entity.AttributeContentItem;
import com.czertainly.core.dao.entity.AttributeDefinition;
import com.czertainly.core.dao.entity.AttributeRelation;
import com.czertainly.core.dao.repository.AttributeContent2ObjectRepository;
import com.czertainly.core.dao.repository.AttributeContentItemRepository;
import com.czertainly.core.dao.repository.AttributeDefinitionRepository;
import com.czertainly.core.dao.repository.AttributeRelationRepository;
import com.czertainly.core.model.SearchFieldObject;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.security.authz.SecurityResourceFilter;
import com.czertainly.core.util.AuthHelper;
import com.czertainly.core.util.SearchHelper;
import com.czertainly.core.util.converter.Sql2PredicateConverter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Transactional
public class AttributeEngine {

    public static final String ATTRIBUTE_DEFINITION_FORCE_UPDATE_LABEL = "<UPDATE_NEEDED>";
    private static final Logger logger = LoggerFactory.getLogger(AttributeEngine.class);
    private static final Pattern UUID_REGEX = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final ObjectMapper ATTRIBUTES_OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @PersistenceContext
    private EntityManager entityManager;
    private AttributeDefinitionRepository attributeDefinitionRepository;
    private AttributeRelationRepository attributeRelationRepository;
    private AttributeContentItemRepository attributeContentItemRepository;
    private AttributeContent2ObjectRepository attributeContent2ObjectRepository;

    private AuthHelper authHelper;

    @Autowired
    public void setAuthHelper(AuthHelper authHelper) {
        this.authHelper = authHelper;
    }

    @Autowired
    public void setAttributeDefinitionRepository(AttributeDefinitionRepository attributeDefinitionRepository) {
        this.attributeDefinitionRepository = attributeDefinitionRepository;
    }

    @Autowired
    public void setAttributeRelationRepository(AttributeRelationRepository attributeRelationRepository) {
        this.attributeRelationRepository = attributeRelationRepository;
    }

    @Autowired
    public void setAttributeContentRepository(AttributeContentItemRepository attributeContentItemRepository) {
        this.attributeContentItemRepository = attributeContentItemRepository;
    }

    @Autowired
    public void setAttributeContent2ObjectRepository(AttributeContent2ObjectRepository attributeContent2ObjectRepository) {
        this.attributeContent2ObjectRepository = attributeContent2ObjectRepository;
    }

    //region Search (Filtering) related methods

    public List<SearchFieldDataByGroupDto> getResourceSearchableFields(Resource resource, boolean settable) {
        final List<SearchFieldDataByGroupDto> searchFieldDataByGroupDtos = new ArrayList<>();

        final List<SearchFieldObject> customAttrSearchFieldObject = settable ? attributeDefinitionRepository.findDistinctAttributeSearchFieldsByResourceAndAttrTypeAndAttrContentTypeAndReadOnlyFalse(resource, List.of(AttributeType.CUSTOM), Arrays.stream(AttributeContentType.values()).filter(AttributeContentType::isFilterByData).toList())
                : attributeDefinitionRepository.findDistinctAttributeSearchFieldsByResourceAndAttrType(resource, List.of(AttributeType.CUSTOM));
        if (!customAttrSearchFieldObject.isEmpty()) {
            searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(SearchHelper.prepareSearchForJSON(customAttrSearchFieldObject), FilterFieldSource.CUSTOM));
        }

        if (!settable) {
            final List<SearchFieldObject> dataAttrSearchFieldObject = attributeDefinitionRepository.findDistinctAttributeSearchFieldsByResourceAndAttrType(resource, List.of(AttributeType.DATA));
            if (!dataAttrSearchFieldObject.isEmpty()) {
                searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(SearchHelper.prepareSearchForJSON(dataAttrSearchFieldObject), FilterFieldSource.DATA));
            }
            final List<SearchFieldObject> metadataSearchFieldObject = attributeDefinitionRepository.findDistinctAttributeSearchFieldsByResourceAndAttrType(resource, List.of(AttributeType.META));
            if (!metadataSearchFieldObject.isEmpty()) {
                searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(SearchHelper.prepareSearchForJSON(metadataSearchFieldObject), FilterFieldSource.META));
            }
        }

        return searchFieldDataByGroupDtos;
    }

    public List<UUID> getResourceObjectUuidsByFilters(Resource resource, SecurityFilter securityFilter, List<SearchFilterRequestDto> searchFilters) {
        List<SearchFilterRequestDto> attributesFilters;
        if (searchFilters == null || searchFilters.isEmpty() || (attributesFilters = searchFilters.stream().filter(f -> f.getFieldSource() == FilterFieldSource.CUSTOM || f.getFieldSource() == FilterFieldSource.META || f.getFieldSource() == FilterFieldSource.DATA).toList()).isEmpty()) {
            return null;
        }

        final List<SearchFieldObject> searchFieldObjects = attributeDefinitionRepository.findDistinctAttributeSearchFieldsByResourceAndAttrType(resource, List.of(AttributeType.CUSTOM, AttributeType.META, AttributeType.DATA));
        final Sql2PredicateConverter.CriteriaQueryDataObject criteriaQueryDataObject = Sql2PredicateConverter.prepareQueryToSearchIntoAttributes(searchFieldObjects, attributesFilters, entityManager.getCriteriaBuilder(), resource);
        return attributeContent2ObjectRepository.findUsingSecurityFilterByCustomCriteriaQuery(securityFilter, criteriaQueryDataObject.getRoot(), criteriaQueryDataObject.getCriteriaQuery(), criteriaQueryDataObject.getPredicate());
    }

    //endregion

    // TODO: return CustomAttribute instead of generic one
    public List<BaseAttribute> getCustomAttributesByResource(Resource resource, SecurityResourceFilter securityResourceFilter) {
        List<AttributeRelation> relations = attributeRelationRepository.findByResourceAndAttributeDefinitionTypeAndAttributeDefinitionEnabled(resource, AttributeType.CUSTOM, true);

        // filter definitions that are not allowed for user
        if (securityResourceFilter.areOnlySpecificObjectsAllowed()) {
            return relations.stream().filter(r -> securityResourceFilter.getAllowedObjects().contains(r.getAttributeDefinition().getUuid())).map(r -> r.getAttributeDefinition().getDefinition()).toList();
        } else {
            return relations.stream().filter(r -> !securityResourceFilter.getForbiddenObjects().contains(r.getAttributeDefinition().getUuid())).map(r -> r.getAttributeDefinition().getDefinition()).toList();
        }
    }

    public DataAttribute getDataAttributeDefinition(UUID connectorUuid, String name) {
        AttributeDefinition definition = attributeDefinitionRepository.findByTypeAndConnectorUuidAndName(AttributeType.DATA, connectorUuid, name).orElse(null);
        if (definition != null) {
            return (DataAttribute) definition.getDefinition();
        }
        return null;
    }

    public List<MetadataAttribute> getMetadataAttributesDefinitionContent(ObjectAttributeContentInfo contentInfo) {
        // TODO: use also operation?
        List<ObjectAttributeDefinitionContent> objectDefinitionContents = attributeContent2ObjectRepository.getObjectAttributeDefinitionContent(AttributeType.META, contentInfo.connectorUuid(), null, contentInfo.objectType(), contentInfo.objectUuid(), contentInfo.sourceObjectType(), contentInfo.sourceObjectUuid());

        Map<String, MetadataAttribute> mapping = new HashMap<>();
        for (ObjectAttributeDefinitionContent objectDefinitionContent : objectDefinitionContents) {
            // check in case data is null because of malformed data
            if (objectDefinitionContent.contentItem().getData() == null) {
                continue;
            }

            String uuid = objectDefinitionContent.uuid().toString();
            MetadataAttribute attribute;
            if ((attribute = mapping.get(uuid)) == null) {
                attribute = (MetadataAttribute) objectDefinitionContent.definition();
                attribute.setContent(new ArrayList<>());
                mapping.put(uuid, attribute);
            }

            attribute.getContent().add(objectDefinitionContent.contentItem());
        }

        return mapping.values().stream().toList();
    }

    // TODO: make it generic to be used also for DATA attributes and update DTOs accordingly
    public List<MetadataResponseDto> getMappedMetadataContent(ObjectAttributeContentInfo contentInfo) {
        List<ObjectAttributeContentDetail> objectMetadataContents = attributeContent2ObjectRepository.getObjectAttributeContentDetail(AttributeType.META, contentInfo.connectorUuid(), null, contentInfo.objectType(), contentInfo.objectUuid(), contentInfo.sourceObjectType(), contentInfo.sourceObjectUuid());

        Map<UUID, String> connectorMapping = new HashMap<>();
        Map<UUID, Map<Resource, Map<UUID, ResponseMetadataDto>>> mapping = new HashMap<>();
        for (ObjectAttributeContentDetail objectMetadataContent : objectMetadataContents) {
            // check in case data is null because of malformed data
            if (objectMetadataContent.contentItem().getData() == null) {
                continue;
            }

            // do we need check for empty content?
            ResponseMetadataDto metadataResponseAttributeDto;
            Map<Resource, Map<UUID, ResponseMetadataDto>> sourceAttributesContentsMapping;
            Map<UUID, ResponseMetadataDto> sourceAttributesContents;
            if (!connectorMapping.containsKey(objectMetadataContent.connectorUuid())) {
//                String connectorName = objectMetadataContent.connectorName() != null ? objectMetadataContent.connectorName() : "<No connector>";
                String connectorName = objectMetadataContent.connectorName();
                connectorMapping.put(objectMetadataContent.connectorUuid(), connectorName);
            }
            if ((sourceAttributesContentsMapping = mapping.get(objectMetadataContent.connectorUuid())) == null) {
                sourceAttributesContentsMapping = new HashMap<>();
                mapping.put(objectMetadataContent.connectorUuid(), sourceAttributesContentsMapping);
            }
            if ((sourceAttributesContents = sourceAttributesContentsMapping.get(objectMetadataContent.sourceObjectType())) == null) {
                sourceAttributesContents = new HashMap<>();
                sourceAttributesContentsMapping.put(objectMetadataContent.sourceObjectType(), sourceAttributesContents);
            }
            if ((metadataResponseAttributeDto = sourceAttributesContents.get(objectMetadataContent.uuid())) == null) {
                metadataResponseAttributeDto = new ResponseMetadataDto();
                metadataResponseAttributeDto.setUuid(objectMetadataContent.uuid().toString());
                metadataResponseAttributeDto.setName(objectMetadataContent.name());
                metadataResponseAttributeDto.setLabel(objectMetadataContent.label());
                metadataResponseAttributeDto.setType(objectMetadataContent.type());
                metadataResponseAttributeDto.setContentType(objectMetadataContent.contentType());
                metadataResponseAttributeDto.setContent(new ArrayList<>());
                sourceAttributesContents.put(objectMetadataContent.uuid(), metadataResponseAttributeDto);
            }

            if (!metadataResponseAttributeDto.getContent().contains(objectMetadataContent.contentItem())) {
                metadataResponseAttributeDto.getContent().add(objectMetadataContent.contentItem());
            }
            if (objectMetadataContent.sourceObjectType() != null) {
                metadataResponseAttributeDto.getSourceObjects().add(new NameAndUuidDto(objectMetadataContent.sourceObjectUuid().toString(), objectMetadataContent.sourceObjectName()));
            }
        }

        List<MetadataResponseDto> metadataResponses = new ArrayList<>();
        for (var connectorSourceAttributes : mapping.entrySet()) {
            for (var sourceAttributes : connectorSourceAttributes.getValue().entrySet()) {
                var metadataResponseDto = new MetadataResponseDto();
                metadataResponseDto.setConnectorUuid(connectorSourceAttributes.getKey() != null ? connectorSourceAttributes.getKey().toString() : null);
                metadataResponseDto.setConnectorName(connectorMapping.get(connectorSourceAttributes.getKey()));
                metadataResponseDto.setSourceObjectType(sourceAttributes.getKey());
                metadataResponseDto.setItems(sourceAttributes.getValue().values().stream().toList());
                metadataResponses.add(metadataResponseDto);
            }
        }

        return metadataResponses;
    }

    public void updateCustomAttributeResources(UUID uuid, List<Resource> resources) throws NotFoundException {
        AttributeDefinition attributeDefinition = attributeDefinitionRepository.findByUuidAndType(uuid, AttributeType.CUSTOM).orElseThrow(() -> new NotFoundException(AttributeDefinition.class, uuid.toString()));
        attributeRelationRepository.deleteAll(attributeRelationRepository.findByAttributeDefinitionUuid(attributeDefinition.getUuid()));
        Set<String> differences = resources.stream().filter(e -> !Resource.getCustomAttributesResources().contains(e)).map(Resource::getCode).collect(Collectors.toSet());
        if (!differences.isEmpty()) {
            throw new ValidationException(ValidationError.create("Unsupported Resources for Custom Attribute binding: " + StringUtils.join(differences, ", ")));
        }

        for (Resource resource : new HashSet<>(resources)) {
            AttributeRelation attributeRelation = new AttributeRelation();
            attributeRelation.setAttributeDefinition(attributeDefinition);
            attributeRelation.setResource(resource);
            attributeRelationRepository.save(attributeRelation);
        }
    }

    public AttributeDefinition updateCustomAttributeDefinition(CustomAttribute customAttribute, List<Resource> resources) throws AttributeException {
        validateAttributeDefinition(customAttribute, null);

        AttributeDefinition attributeDefinition = attributeDefinitionRepository.findByAttributeUuid(UUID.fromString(customAttribute.getUuid())).orElse(null);
        boolean newCustomAttribute = attributeDefinition == null;
        if (newCustomAttribute) {
            attributeDefinition = new AttributeDefinition();
            attributeDefinition.setUuid(UUID.fromString(customAttribute.getUuid()));
            attributeDefinition.setName(customAttribute.getName());
            attributeDefinition.setType(AttributeType.CUSTOM);
            attributeDefinition.setAttributeUuid(attributeDefinition.getUuid());
            attributeDefinition.setContentType(customAttribute.getContentType());
            // Default state of the attribute will always be enabled
            attributeDefinition.setEnabled(true);
        } else {
            if (attributeDefinition.getContentType() != customAttribute.getContentType()) {
                throw new AttributeException(String.format("Custom attribute content type changed to %s while stored attribute definition have content type %s", customAttribute.getContentType().getLabel(), attributeDefinition.getContentType().getLabel()), customAttribute.getUuid(), customAttribute.getName(), customAttribute.getType(), null);
            }
        }

        attributeDefinition.setLabel(customAttribute.getProperties().getLabel());
        attributeDefinition.setRequired(customAttribute.getProperties().isRequired());
        attributeDefinition.setReadOnly(customAttribute.getProperties().isReadOnly());
        attributeDefinition.setDefinition(customAttribute);
        attributeDefinition = attributeDefinitionRepository.save(attributeDefinition);

        // save relations
        if (resources != null) {
            if (!newCustomAttribute) {
                attributeDefinition.getRelations().clear();
                attributeRelationRepository.deleteByAttributeDefinitionUuid(attributeDefinition.getUuid());
            }
            if (!resources.isEmpty()) {
                // check for invalid resources
                List<String> invalidResources = resources.stream().filter(r -> !r.hasCustomAttributes()).map(Resource::getLabel).toList();
                if (!invalidResources.isEmpty()) {
                    throw new AttributeException("Unsupported Resources for Custom Attribute: " + StringUtils.join(invalidResources, ", "), customAttribute.getUuid(), customAttribute.getName(), customAttribute.getType(), null);
                }

                for (Resource resource : new HashSet<>(resources)) {
                    AttributeRelation attributeRelation = new AttributeRelation();
                    attributeRelation.setAttributeDefinition(attributeDefinition);
                    attributeRelation.setResource(resource);
                    attributeRelationRepository.save(attributeRelation);
                    attributeDefinition.getRelations().add(attributeRelation);
                }
            }
        }

        return attributeDefinition;
    }

    public void validateUpdateDataAttributes(UUID connectorUuid, String operation, List<BaseAttribute> attributes, List<RequestAttributeDto> requestAttributes) throws AttributeException {
        updateDataAttributeDefinitions(connectorUuid, operation, attributes);
        validateDataAttributesContent(connectorUuid, operation, attributes, requestAttributes);
    }

    private void validateDataAttributesContent(UUID connectorUuid, String operation, List<BaseAttribute> attributes, List<RequestAttributeDto> requestAttributes) throws ValidationException {
        logger.debug("Validating data attributes: {}", attributes);
        if (attributes == null) {
            attributes = new ArrayList<>();
        }

        // alternative is to load all definitions by connector UUID and operation
        // TODO: what to do with orphaned (old) definitions from connector that were replaced and would be still validated and asked to be filled?
        List<UUID> attributeUuids = attributes.stream().filter(a -> a.getType() == AttributeType.DATA).map(a -> UUID.fromString(a.getUuid())).toList();
        List<String> attributeNames = attributes.stream().filter(a -> a.getType() == AttributeType.DATA).map(BaseAttribute::getName).toList();
        Map<String, AttributeDefinition> definitionsMapping = attributeDefinitionRepository.findByTypeAndConnectorUuidAndAttributeUuidInAndNameIn(AttributeType.DATA, connectorUuid, attributeUuids, attributeNames).stream().collect(Collectors.toMap(AttributeDefinition::getName, d -> d));

        // load missing data attributes definitions from DB
        for (RequestAttributeDto requestAttributeDto : requestAttributes) {
            if (definitionsMapping.get(requestAttributeDto.getName()) == null) {
                AttributeDefinition missingDefinition = attributeDefinitionRepository.findByTypeAndConnectorUuidAndAttributeUuidAndName(AttributeType.DATA, connectorUuid, UUID.fromString(requestAttributeDto.getUuid()), requestAttributeDto.getName()).orElse(null);
                if (missingDefinition != null) {
                    // update operation - if attribute is retrieved by callback, we do not know its operation
                    if (!Objects.equals(missingDefinition.getOperation(), operation)) {
                        missingDefinition.setOperation(operation);
                        attributeDefinitionRepository.save(missingDefinition);
                    }
                    definitionsMapping.put(requestAttributeDto.getName(), missingDefinition);
                }
            }
        }

        // no attributes to validate
        if (definitionsMapping.isEmpty() && requestAttributes.isEmpty()) {
            return;
        }

        // check for general attributes validation
        List<ValidationError> errors = validateAttributesContent(definitionsMapping, requestAttributes);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    public void updateDataAttributeDefinitions(UUID connectorUuid, String operation, List<BaseAttribute> attributes) throws AttributeException {
        if (attributes == null) {
            return;
        }
        for (BaseAttribute attribute : attributes) {
            if (attribute.getType() == AttributeType.DATA) {
                updateDataAttributeDefinition(connectorUuid, operation, (DataAttribute) attribute);
            }
        }
    }

    private void updateDataAttributeDefinition(UUID connectorUuid, String operation, DataAttribute dataAttribute) throws AttributeException {
        validateAttributeDefinition(dataAttribute, connectorUuid);

        // find by connector uuid and name only because attribute uuid could be generated when data attribute was migrated from RequestAttributeDto
        AttributeDefinition attributeDefinition = attributeDefinitionRepository.findByTypeAndConnectorUuidAndAttributeUuidAndName(AttributeType.DATA, connectorUuid, UUID.fromString(dataAttribute.getUuid()), dataAttribute.getName()).orElse(null);
        if (attributeDefinition != null) {
            // update definition when it was migrated from RequestAttributeDto
            if (attributeDefinition.getLabel().isEmpty() && attributeDefinition.getDefinition().getDescription().equals(ATTRIBUTE_DEFINITION_FORCE_UPDATE_LABEL)) {
                attributeDefinition.setContentType(dataAttribute.getContentType());
                attributeDefinition.setAttributeUuid(UUID.fromString(dataAttribute.getUuid()));
            }
            // check for change of content type
            else if (attributeDefinition.getContentType() != dataAttribute.getContentType()) {
                throw new AttributeException(String.format("Connector attribute content type changed to %s while stored attribute definition have content type %s", dataAttribute.getContentType().getLabel(), attributeDefinition.getContentType().getLabel()), dataAttribute.getUuid(), dataAttribute.getName(), dataAttribute.getType(), connectorUuid.toString());
            }
        } else {
            attributeDefinition = new AttributeDefinition();
            attributeDefinition.setConnectorUuid(connectorUuid);
            attributeDefinition.setAttributeUuid(UUID.fromString(dataAttribute.getUuid()));
            attributeDefinition.setName(dataAttribute.getName());
            attributeDefinition.setType(AttributeType.DATA);
            attributeDefinition.setContentType(dataAttribute.getContentType());
            attributeDefinition.setOperation(operation);
        }
        attributeDefinition.setLabel(dataAttribute.getProperties().getLabel());
        attributeDefinition.setRequired(dataAttribute.getProperties().isRequired());
        attributeDefinition.setReadOnly(dataAttribute.getProperties().isReadOnly());

        // we need content only for readonly attribute
        if (!Boolean.TRUE.equals(attributeDefinition.isReadOnly())) {
            dataAttribute.setContent(null);
        }
        attributeDefinition.setDefinition(dataAttribute);
        attributeDefinitionRepository.save(attributeDefinition);
    }

    public AttributeDefinition updateMetadataAttributeDefinition(MetadataAttribute metadataAttribute, UUID connectorUuid) throws AttributeException {
        var isGlobal = metadataAttribute.getProperties().isGlobal();
        if (connectorUuid == null && !isGlobal) {
            throw new AttributeException("Cannot update metadata without specifying connector UUID.", metadataAttribute.getUuid(), metadataAttribute.getName(), metadataAttribute.getType(), null);
        }
        validateAttributeDefinition(metadataAttribute, connectorUuid);

        AttributeDefinition attributeDefinition = null;
        if (isGlobal) {
            attributeDefinition = attributeDefinitionRepository.findByTypeAndNameAndGlobal(AttributeType.META, metadataAttribute.getName(), true).orElse(null);
        }
        if (attributeDefinition == null) {
            attributeDefinition = attributeDefinitionRepository.findByTypeAndConnectorUuidAndAttributeUuidAndName(AttributeType.META, connectorUuid, UUID.fromString(metadataAttribute.getUuid()), metadataAttribute.getName()).orElse(null);
        }
        if (attributeDefinition != null) {
            // check for change of content type
            if (attributeDefinition.getContentType() != metadataAttribute.getContentType()) {
                throw new AttributeException(String.format("Metadata attribute content type changed to %s while stored attribute definition have content type %s", metadataAttribute.getContentType().getLabel(), attributeDefinition.getContentType().getLabel()), metadataAttribute.getUuid(), metadataAttribute.getName(), metadataAttribute.getType(), connectorUuid == null ? null : connectorUuid.toString());
            }
        } else {
            attributeDefinition = new AttributeDefinition();
            attributeDefinition.setConnectorUuid(connectorUuid);
            attributeDefinition.setAttributeUuid(UUID.fromString(metadataAttribute.getUuid()));
            attributeDefinition.setName(metadataAttribute.getName());
            attributeDefinition.setType(AttributeType.META);
            attributeDefinition.setContentType(metadataAttribute.getContentType());
//            attributeDefinition.setOperation(operation);
            attributeDefinition.setGlobal(isGlobal);
        }
        attributeDefinition.setLabel(metadataAttribute.getProperties().getLabel());

        // we don't need content in definition
        metadataAttribute.setContent(List.of());
        attributeDefinition.setDefinition(metadataAttribute);
        attributeDefinitionRepository.save(attributeDefinition);

        return attributeDefinition;
    }

    public void updateMetadataAttributes(List<MetadataAttribute> attributes, ObjectAttributeContentInfo objectAttributeContentInfo) throws AttributeException {
        if (objectAttributeContentInfo.connectorUuid() == null) {
            throw new AttributeException("Cannot update metadata without specifying connector UUID.");
        }
        if (attributes == null) {
            return;
        }

        for (MetadataAttribute metadataAttribute : attributes) {
            if (metadataAttribute.getType() != AttributeType.META) {
                continue;
            }

            updateMetadataAttribute(metadataAttribute, objectAttributeContentInfo);
        }
    }

    public void updateMetadataAttribute(MetadataAttribute metadataAttribute, ObjectAttributeContentInfo objectAttributeContentInfo) throws AttributeException {
        UUID connectorUuid = objectAttributeContentInfo.connectorUuid();
        List<BaseAttributeContent> contentItems = metadataAttribute.getContent();
        AttributeDefinition attributeDefinition = updateMetadataAttributeDefinition(metadataAttribute, connectorUuid);

        if (objectAttributeContentInfo.connectorUuid() == null) {
            throw new AttributeException("Cannot update metadata content without specifying connector UUID.");
        }

        // delete content of metadata for this object as its content should be replaced
        if (metadataAttribute.getProperties().isOverwrite()) {
            deleteObjectAttributeDefinitionContent(attributeDefinition.getUuid(), objectAttributeContentInfo.objectType(), objectAttributeContentInfo.objectUuid());
        }
        createObjectAttributeContent(attributeDefinition, objectAttributeContentInfo, contentItems);
    }

    public List<DataAttribute> getDefinitionObjectAttributeContent(AttributeType attributeType, UUID connectorUuid, String operation, Resource objectType, UUID objectUuid) {
        logger.debug("Getting the {} attributes for {} with UUID: {}", attributeType.getLabel(), objectType.getLabel(), objectUuid);
        List<ObjectAttributeDefinitionContent> objectDefinitionContents = attributeContent2ObjectRepository.getObjectAttributeDefinitionContent(attributeType, connectorUuid, operation, objectType, objectUuid, null, null);

        Map<String, DataAttribute> mapping = new HashMap<>();
        for (ObjectAttributeDefinitionContent objectDefinitionContent : objectDefinitionContents) {
            String uuid = objectDefinitionContent.uuid().toString();
            DataAttribute attribute;
            if ((attribute = mapping.get(uuid)) == null) {
                attribute = (DataAttribute) objectDefinitionContent.definition();
                attribute.setContent(new ArrayList<>());
                mapping.put(uuid, attribute);
            }

            attribute.getContent().add(objectDefinitionContent.contentItem());
        }

        return mapping.values().stream().toList();
    }

    public List<ResponseAttributeDto> getObjectCustomAttributesContent(Resource objectType, UUID objectUuid) {
        logger.debug("Getting the custom attributes for {} with UUID: {}", objectType.getLabel(), objectUuid);
        SecurityResourceFilter securityResourceFilter = loadCustomAttributesSecurityResourceFilter();

        return getObjectCustomAttributesContent(objectType, objectUuid, securityResourceFilter);
    }

    private List<ResponseAttributeDto> getObjectCustomAttributesContent(Resource objectType, UUID objectUuid, SecurityResourceFilter securityResourceFilter) {
        List<UUID> allowedAttributes = null;
        List<UUID> forbiddenAttributes = null;
        if (securityResourceFilter != null) {
            if (securityResourceFilter.areOnlySpecificObjectsAllowed()) {
                allowedAttributes = securityResourceFilter.getAllowedObjects();
                if (allowedAttributes.isEmpty()) allowedAttributes.add(null);
            } else if (!securityResourceFilter.getForbiddenObjects().isEmpty()) {
                forbiddenAttributes = securityResourceFilter.getForbiddenObjects();
            }
        }

        List<ObjectAttributeContent> objectContents = attributeContent2ObjectRepository.getObjectCustomAttributesContent(AttributeType.CUSTOM, objectType, objectUuid, allowedAttributes, forbiddenAttributes);
        return getResponseAttributes(objectContents);
    }

    public List<ResponseAttributeDto> getObjectDataAttributesContent(UUID connectorUuid, String operation, Resource objectType, UUID objectUuid) {
        logger.debug("Getting the data attributes for {} with UUID {} from connector {} and operation {}.", objectType.getLabel(), objectUuid, connectorUuid, operation);
        List<ObjectAttributeContent> objectContents = loadDataAttributesContent(connectorUuid, operation, objectType, objectUuid);
        return getResponseAttributes(objectContents);
    }

    public List<RequestAttributeDto> getRequestObjectDataAttributesContent(UUID connectorUuid, String operation, Resource objectType, UUID objectUuid) {
        logger.debug("Getting the request data attributes for {} with UUID {} from connector {} and operation {}.", objectType.getLabel(), objectUuid, connectorUuid, operation);
        List<ObjectAttributeContent> objectContents = loadDataAttributesContent(connectorUuid, operation, objectType, objectUuid);
        return getRequestAttributes(objectContents);
    }

    public List<DataAttribute> getDataAttributesByContent(UUID connectorUuid, List<RequestAttributeDto> requestAttributes) throws AttributeException {
        List<DataAttribute> dataAttributes = new ArrayList<>();
        String connectorUuidStr = connectorUuid == null ? null : connectorUuid.toString();
        for (RequestAttributeDto requestAttribute : requestAttributes) {
            AttributeDefinition definition = attributeDefinitionRepository.findByTypeAndConnectorUuidAndAttributeUuidAndName(AttributeType.DATA, connectorUuid, UUID.fromString(requestAttribute.getUuid()), requestAttribute.getName())
                    .orElseThrow(() -> new AttributeException("Missing data attribute definition", requestAttribute.getUuid() == null ? null : requestAttribute.getUuid(), requestAttribute.getName(), AttributeType.DATA, connectorUuidStr));

            validateAttributeContent(definition, requestAttribute.getContent());

            DataAttribute dataAttribute = (DataAttribute) definition.getDefinition();
            dataAttribute.setContent(requestAttribute.getContent());
            dataAttributes.add(dataAttribute);
        }

        return dataAttributes;
    }

    private List<ObjectAttributeContent> loadDataAttributesContent(UUID connectorUuid, String operation, Resource objectType, UUID objectUuid) {
        List<ObjectAttributeContent> objectContents;
        if (operation != null && connectorUuid != null) {
            objectContents = attributeContent2ObjectRepository.getObjectDataAttributesContent(AttributeType.DATA, connectorUuid, operation, objectType, objectUuid);
        } else if (operation != null) {
            objectContents = attributeContent2ObjectRepository.getObjectDataAttributesContentNoConnector(AttributeType.DATA, operation, objectType, objectUuid);
        } else if (connectorUuid != null) {
            objectContents = attributeContent2ObjectRepository.getObjectDataAttributesContentNoOperation(AttributeType.DATA, connectorUuid, objectType, objectUuid);
        } else {
            objectContents = attributeContent2ObjectRepository.getObjectDataAttributesContentNoConnectorNoOperation(AttributeType.DATA, objectType, objectUuid);
        }

        return objectContents;
    }

    private List<RequestAttributeDto> getRequestAttributes(List<ObjectAttributeContent> objectContents) {
        Map<String, RequestAttributeDto> mapping = new HashMap<>();
        for (ObjectAttributeContent objectContent : objectContents) {
            String uuid = objectContent.uuid().toString();
            RequestAttributeDto requestAttribute;
            if ((requestAttribute = mapping.get(uuid)) == null) {
                requestAttribute = new RequestAttributeDto();
                requestAttribute.setUuid(objectContent.uuid().toString());
                requestAttribute.setName(objectContent.name());
                requestAttribute.setContentType(objectContent.contentType());
                requestAttribute.setContent(new ArrayList<>());
                mapping.put(uuid, requestAttribute);
            }
            requestAttribute.getContent().add(objectContent.contentItem());
        }

        return mapping.values().stream().toList();
    }

    private List<ResponseAttributeDto> getResponseAttributes(List<ObjectAttributeContent> objectContents) {
        Map<String, ResponseAttributeDto> mapping = new HashMap<>();
        for (ObjectAttributeContent objectContent : objectContents) {
            String uuid = objectContent.uuid().toString();
            ResponseAttributeDto responseAttribute;
            if ((responseAttribute = mapping.get(uuid)) == null) {
                responseAttribute = new ResponseAttributeDto();
                responseAttribute.setUuid(objectContent.uuid().toString());
                responseAttribute.setName(objectContent.name());
                responseAttribute.setLabel(objectContent.label());
                responseAttribute.setType(objectContent.type());
                responseAttribute.setContentType(objectContent.contentType());
                responseAttribute.setContent(new ArrayList<>());
                mapping.put(uuid, responseAttribute);
            }
            responseAttribute.getContent().add(objectContent.contentItem());
        }

        return mapping.values().stream().toList();
    }

    public List<ResponseAttributeDto> updateObjectDataAttributesContent(UUID connectorUuid, String operation, Resource objectType, UUID objectUuid, List<RequestAttributeDto> requestAttributes) throws ValidationException, NotFoundException, AttributeException {
        logger.debug("Updating the content of data attributes for resource {} with UUID: {}", objectType.getLabel(), objectUuid);
        if (requestAttributes == null) {
            requestAttributes = new ArrayList<>();
        }

        // delete all content for operation
        ObjectAttributeContentInfo objectAttributeContentInfo = new ObjectAttributeContentInfo(connectorUuid, objectType, objectUuid);
        deleteOperationObjectAttributesContent(AttributeType.DATA, operation, objectAttributeContentInfo);
        for (RequestAttributeDto requestAttribute : requestAttributes) {
            AttributeDefinition attributeDefinition = attributeDefinitionRepository.findByTypeAndConnectorUuidAndAttributeUuidAndName(AttributeType.DATA, connectorUuid, UUID.fromString(requestAttribute.getUuid()), requestAttribute.getName()).orElseThrow(() -> new NotFoundException(AttributeDefinition.class, requestAttribute.getName()));
            createObjectAttributeContent(attributeDefinition, objectAttributeContentInfo, requestAttribute.getContent());
        }

        return getObjectDataAttributesContent(connectorUuid, operation, objectType, objectUuid);
    }

    public List<ResponseAttributeDto> updateObjectCustomAttributesContent(Resource objectType, UUID objectUuid, List<RequestAttributeDto> requestAttributes) throws ValidationException, NotFoundException, AttributeException {
        logger.debug("Updating the content of custom attributes for resource {} with UUID: {}", objectType.getLabel(), objectUuid);
        if (requestAttributes == null) {
            requestAttributes = new ArrayList<>();
        }

        SecurityResourceFilter securityResourceFilter = loadCustomAttributesSecurityResourceFilter();
        validateCustomAttributesContent(objectType, requestAttributes, securityResourceFilter);

        if (securityResourceFilter == null) {
            // custom attributes content is automatically replaced
            deleteObjectAttributeContentByType(AttributeType.CUSTOM, objectType, objectUuid);
            for (RequestAttributeDto requestAttribute : requestAttributes) {
                AttributeDefinition attributeDefinition = attributeDefinitionRepository.findByTypeAndName(AttributeType.CUSTOM, requestAttribute.getName()).orElseThrow(() -> new NotFoundException(AttributeDefinition.class, requestAttribute.getName()));
                createObjectAttributeContent(attributeDefinition, new ObjectAttributeContentInfo(objectType, objectUuid), requestAttribute.getContent());
            }
        } else {
            for (RequestAttributeDto requestAttribute : requestAttributes) {
                AttributeDefinition attributeDefinition = attributeDefinitionRepository.findByTypeAndName(AttributeType.CUSTOM, requestAttribute.getName()).orElseThrow(() -> new NotFoundException(AttributeDefinition.class, requestAttribute.getName()));
                if ((securityResourceFilter.areOnlySpecificObjectsAllowed())) {
                    if (!securityResourceFilter.getAllowedObjects().contains(attributeDefinition.getUuid())) {
                        throw new AttributeException(String.format("Updating custom attribute `%s` is not allowed", attributeDefinition.getName()));
                    }
                } else {
                    if (securityResourceFilter.getForbiddenObjects().contains(attributeDefinition.getUuid())) {
                        throw new AttributeException(String.format("Updating custom attribute `%s` is not allowed", attributeDefinition.getName()));
                    }
                }

                deleteObjectAttributeDefinitionContent(attributeDefinition.getUuid(), objectType, objectUuid);
                createObjectAttributeContent(attributeDefinition, new ObjectAttributeContentInfo(objectType, objectUuid), requestAttribute.getContent());
            }
        }

        return getObjectCustomAttributesContent(objectType, objectUuid, securityResourceFilter);
    }

    public void updateObjectCustomAttributeContent(Resource objectType, UUID objectUuid, UUID definitionUuid, String attributeName, List<BaseAttributeContent> attributeContentItems) throws NotFoundException, AttributeException {
        AttributeDefinition attributeDefinition;
        if (definitionUuid != null) {
            attributeDefinition = attributeDefinitionRepository.findByUuid(definitionUuid).orElseThrow(() -> new NotFoundException(AttributeDefinition.class, definitionUuid.toString()));
        } else {
            attributeDefinition = attributeDefinitionRepository.findByTypeAndName(AttributeType.CUSTOM, attributeName).orElseThrow(() -> new NotFoundException(AttributeDefinition.class, attributeName.toString()));
        }
        if (attributeDefinition.getType() != AttributeType.CUSTOM) {
            throw new AttributeException("Cannot update content of attribute. Only custom attributes are allowed to be updated directly.", attributeDefinition.getUuid().toString(), attributeDefinition.getName(), attributeDefinition.getType(), null);
        }
        if (!Boolean.TRUE.equals(attributeDefinition.isEnabled())) {
            throw new AttributeException("Cannot update content of disabled attribute.", attributeDefinition.getUuid().toString(), attributeDefinition.getName(), attributeDefinition.getType(), null);
        }

        AttributeRelation relation = attributeRelationRepository.findByResourceAndAttributeDefinitionUuidAndAttributeDefinitionTypeAndAttributeDefinitionEnabled(objectType, attributeDefinition.getUuid(), AttributeType.CUSTOM, true).orElseThrow(() -> new AttributeException("Cannot update content of attribute since it is not associated with resource " + objectType.getLabel(), attributeDefinition.getUuid().toString(), attributeDefinition.getName(), attributeDefinition.getType(), null));

        // filter out updating
        SecurityResourceFilter securityResourceFilter = loadCustomAttributesSecurityResourceFilter();
        if (securityResourceFilter != null) {
            if ((securityResourceFilter.areOnlySpecificObjectsAllowed())) {
                if (!securityResourceFilter.getAllowedObjects().contains(definitionUuid)) {
                    throw new AttributeException(String.format("Updating custom attribute `%s` is not allowed", attributeDefinition.getName()));
                }
            } else {
                if (securityResourceFilter.getForbiddenObjects().contains(definitionUuid)) {
                    throw new AttributeException(String.format("Updating custom attribute `%s` is not allowed", attributeDefinition.getName()));
                }
            }
        }

        // custom attributes content is automatically replaced
        deleteObjectAttributeDefinitionContent(attributeDefinition.getUuid(), objectType, objectUuid);
        if (attributeContentItems != null && !attributeContentItems.isEmpty()) {
            validateAttributeContent(attributeDefinition, attributeContentItems);
            createObjectAttributeContent(attributeDefinition, new ObjectAttributeContentInfo(objectType, objectUuid), attributeContentItems);
        }
    }

    private void validateAttributeDefinition(BaseAttribute<?> attribute, UUID connectorUuid) throws AttributeException {
        String connectorUuidStr = connectorUuid == null ? null : connectorUuid.toString();
        if (attribute.getUuid() == null || !UUID_REGEX.matcher(attribute.getUuid()).matches()) {
            throw new AttributeException("Attribute does not have valid UUID", attribute.getUuid(), attribute.getName(), attribute.getType(), connectorUuidStr);
        }
        if (attribute.getName() == null || attribute.getName().isBlank()) {
            throw new AttributeException("Attribute does not have valid name", attribute.getUuid(), attribute.getName(), attribute.getType(), connectorUuidStr);
        }

        if (attribute.getType() == AttributeType.GROUP) {
            GroupAttribute groupAttribute = (GroupAttribute) attribute;
            if (groupAttribute.getAttributeCallback() == null) {
                throw new AttributeException("Group attribute does not have callback", attribute.getUuid(), attribute.getName(), attribute.getType(), connectorUuidStr);
            }
        } else if (attribute.getType() == AttributeType.CUSTOM || attribute.getType() == AttributeType.DATA) {
            String label;
            boolean readOnly, list, multiSelect, hasCallback, hasContent;
            if (attribute.getType() == AttributeType.CUSTOM) {
                CustomAttribute customAttribute = (CustomAttribute) attribute;

                label = customAttribute.getProperties().getLabel();
                readOnly = customAttribute.getProperties().isReadOnly();
                list = customAttribute.getProperties().isList();
                multiSelect = customAttribute.getProperties().isMultiSelect();
                hasCallback = false;
                hasContent = customAttribute.getContent() != null && !customAttribute.getContent().isEmpty();
            } else {
                DataAttribute dataAttribute = (DataAttribute) attribute;

                label = dataAttribute.getProperties().getLabel();
                readOnly = dataAttribute.getProperties().isReadOnly();
                list = dataAttribute.getProperties().isList();
                multiSelect = dataAttribute.getProperties().isMultiSelect();
                hasCallback = dataAttribute.getAttributeCallback() != null;
                hasContent = dataAttribute.getContent() != null && !dataAttribute.getContent().isEmpty();
            }

            if (label == null || label.isBlank()) {
                throw new AttributeException("Attribute does not have label", attribute.getUuid(), attribute.getName(), attribute.getType(), connectorUuidStr);
            }

            if (multiSelect && !list) {
                throw new AttributeException("Attribute has to be defined as list to be multiselect", attribute.getUuid(), attribute.getName(), attribute.getType(), connectorUuidStr);
            }

            if (readOnly) {
                if (hasCallback) {
                    throw new AttributeException("Read only attribute cannot have callback", attribute.getUuid(), attribute.getName(), attribute.getType(), connectorUuidStr);
                }
                if (!hasContent) {
                    throw new AttributeException("Read only attribute must define its content", attribute.getUuid(), attribute.getName(), attribute.getType(), connectorUuidStr);
                }
                if (list) {
                    throw new AttributeException("Read only attribute cannot be list", attribute.getUuid(), attribute.getName(), attribute.getType(), connectorUuidStr);
                }
            }
        }
    }

    public void validateCustomAttributesContent(Resource resource, List<RequestAttributeDto> attributes) throws ValidationException {
        logger.debug("Validating custom attributes: {}", attributes);
        SecurityResourceFilter securityResourceFilter = loadCustomAttributesSecurityResourceFilter();
        validateCustomAttributesContent(resource, attributes, securityResourceFilter);
    }

    private void validateCustomAttributesContent(Resource resource, List<RequestAttributeDto> attributes, SecurityResourceFilter securityResourceFilter) throws ValidationException {
        if (attributes == null) {
            attributes = new ArrayList<>();
        }

        List<AttributeRelation> relations = attributeRelationRepository.findByResourceAndAttributeDefinitionType(resource, AttributeType.CUSTOM);

        // filter definitions that are not allowed for user
        Map<String, AttributeDefinition> definitionsMapping;
        if (securityResourceFilter != null) {
            if (securityResourceFilter.areOnlySpecificObjectsAllowed()) {
                definitionsMapping = relations.stream().filter(r -> securityResourceFilter.getAllowedObjects().contains(r.getAttributeDefinition().getUuid())).collect(Collectors.toMap(r -> r.getAttributeDefinition().getName(), AttributeRelation::getAttributeDefinition));
                attributes = attributes.stream().filter(a -> securityResourceFilter.getAllowedObjects().contains(UUID.fromString(a.getUuid()))).toList();
            } else {
                definitionsMapping = relations.stream().filter(r -> !securityResourceFilter.getForbiddenObjects().contains(r.getAttributeDefinition().getUuid())).collect(Collectors.toMap(r -> r.getAttributeDefinition().getName(), AttributeRelation::getAttributeDefinition));
                attributes = attributes.stream().filter(a -> !securityResourceFilter.getForbiddenObjects().contains(UUID.fromString(a.getUuid()))).toList();
            }
        } else {
            definitionsMapping = relations.stream().collect(Collectors.toMap(r -> r.getAttributeDefinition().getName(), AttributeRelation::getAttributeDefinition));
        }

        // no attributes to validate
        if (definitionsMapping.isEmpty() && attributes.isEmpty()) {
            return;
        }

        // check for custom attributes specific validation
        List<ValidationError> errors = new ArrayList<>();
        for (RequestAttributeDto attribute : attributes) {
            AttributeDefinition definition = definitionsMapping.get(attribute.getName());
            if (definition == null) {
                errors.add(ValidationError.create("Content for custom attribute {} is provided but resource {} is not associated with it", attribute.getName(), resource.getLabel()));
                continue;
            }
            if (!Boolean.TRUE.equals(definition.isEnabled())) {
                errors.add(ValidationError.create("Content for custom attribute {} is provided but attribute is disabled", attribute.getName(), resource.getLabel()));
            }
        }

        // check for general attributes validation
        if (errors.isEmpty()) {
            errors = validateAttributesContent(definitionsMapping, attributes);
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    public void deleteConnectorAttributeDefinitionsContent(UUID connectorUuid) {
        // delete data attributes with content
        logger.debug("Deleting data attribute definitions for connector with UUID {}", connectorUuid);
        attributeContent2ObjectRepository.deleteByAttributeContentItemAttributeDefinitionTypeAndConnectorUuid(AttributeType.DATA, connectorUuid);
        attributeContentItemRepository.deleteByAttributeDefinitionTypeAndAttributeDefinitionConnectorUuid(AttributeType.DATA, connectorUuid);
        long deletedDefinitions = attributeDefinitionRepository.deleteByTypeAndConnectorUuid(AttributeType.DATA, connectorUuid);
        logger.debug("Deleted {} data attribute definitions for connector with UUID {}", deletedDefinitions, connectorUuid);

        // remove connector reference from metadata definitions and content
        // WARNING: connector uuid is removed from all content disregarding attribute type since connector data attributes content was already removed in step before and custom attributes are not linked to connector so it is safe
        attributeDefinitionRepository.removeConnectorByTypeAndConnectorUuid(AttributeType.META, connectorUuid);
        attributeContent2ObjectRepository.removeConnectorByConnectorUuid(connectorUuid);
    }

    public void deleteAttributeDefinition(AttributeType attributeType, UUID definitionUuid) throws NotFoundException {
        AttributeDefinition definition = attributeDefinitionRepository.findByUuidAndType(definitionUuid, attributeType).orElseThrow(() -> new NotFoundException(AttributeDefinition.class, definitionUuid.toString()));
        deleteAllAttributeDefinitionContent(definitionUuid);
        attributeDefinitionRepository.delete(definition);
    }

    public void deleteAttributeDefinition(AttributeType attributeType, UUID connectorUuid, UUID attributeUuid, String name) throws NotFoundException {
        AttributeDefinition definition = attributeDefinitionRepository.findByTypeAndConnectorUuidAndAttributeUuidAndName(attributeType, connectorUuid, attributeUuid, name).orElseThrow(() -> new NotFoundException(AttributeDefinition.class, attributeUuid));
        deleteAllAttributeDefinitionContent(definition.getUuid());
        attributeDefinitionRepository.delete(definition);
    }

    public void deleteAllObjectAttributeContent(Resource objectType, UUID objectUuid) {
        logger.debug("Deleting the attribute content for resource {} with UUID: {}", objectType.getLabel(), objectUuid);
        long deletedCount = attributeContent2ObjectRepository.deleteByObjectTypeAndObjectUuid(objectType, objectUuid);
        logger.debug("Deleted {} attribute content items for {} with UUID {}", deletedCount, objectType.getLabel(), objectUuid);
    }

    public void deleteObjectAttributesContent(AttributeType attributeType, ObjectAttributeContentInfo contentInfo) {
        logger.debug("Deleting the {} attribute content for resource {} with UUID {}. Info: {}", attributeType.getLabel(), contentInfo.objectType().getLabel(), contentInfo.objectUuid(), contentInfo);
        long deletedCount = attributeContent2ObjectRepository.deleteByAttributeContentItemAttributeDefinitionTypeAndConnectorUuidAndObjectTypeAndObjectUuidAndSourceObjectTypeAndSourceObjectUuid(attributeType, contentInfo.connectorUuid(), contentInfo.objectType(), contentInfo.objectUuid(), contentInfo.sourceObjectType(), contentInfo.sourceObjectUuid());
        logger.debug("Deleted {} attribute content items for {} with UUID {}", deletedCount, contentInfo.objectType().getLabel(), contentInfo.objectUuid());
    }

    public void deleteOperationObjectAttributesContent(AttributeType attributeType, String operation, ObjectAttributeContentInfo contentInfo) {
        logger.debug("Deleting the {} attribute content of operation {} for resource {} with UUID {}. Info: {}", attributeType.getLabel(), operation, contentInfo.objectType().getLabel(), contentInfo.objectUuid(), contentInfo);
        long deletedCount = attributeContent2ObjectRepository.deleteByAttributeContentItemAttributeDefinitionTypeAndAttributeContentItemAttributeDefinitionOperationAndConnectorUuidAndObjectTypeAndObjectUuidAndSourceObjectTypeAndSourceObjectUuid(attributeType, operation, contentInfo.connectorUuid(), contentInfo.objectType(), contentInfo.objectUuid(), contentInfo.sourceObjectType(), contentInfo.sourceObjectUuid());
        logger.debug("Deleted {} attribute content items for {} with UUID {}", deletedCount, contentInfo.objectType().getLabel(), contentInfo.objectUuid());
    }

    private void createObjectAttributeContent(AttributeDefinition attributeDefinition, ObjectAttributeContentInfo objectAttributeContentInfo, List<BaseAttributeContent> attributeContentItems) throws AttributeException {
        logger.debug("Creating the attribute content for attribute {} of type {}. Info: {}", attributeDefinition.getName(), attributeDefinition.getType().getLabel(), objectAttributeContentInfo);

        validateAttributeContent(attributeDefinition, attributeContentItems);
        for (int i = 0; i < attributeContentItems.size(); i++) {
            AttributeContentItem contentItemEntity;
            BaseAttributeContent<?> attributeContentItem = attributeContentItems.get(i);
            Optional<AttributeContentItem> contentItemEntityResponse = attributeContentItemRepository.findByJsonAndAttributeDefinitionUuid(attributeContentItem, attributeDefinition.getUuid());

            // check if content item for this attribute definition exists to don't create duplicate items
            if (contentItemEntityResponse.isPresent()) {
                contentItemEntity = contentItemEntityResponse.get();

                // check if that content item is not already assigned to same object for meta attribute
                // TODO: do we need to allow duplicate content items for one attribute definition? Maybe if attribute is list or do this check just for META attributes?
                var aco = attributeContent2ObjectRepository.getByConnectorUuidAndAttributeContentItemUuidAndObjectTypeAndObjectUuidAndSourceObjectTypeAndSourceObjectUuid(objectAttributeContentInfo.connectorUuid(), contentItemEntity.getUuid(), objectAttributeContentInfo.objectType(), objectAttributeContentInfo.objectUuid(), objectAttributeContentInfo.sourceObjectType(), objectAttributeContentInfo.sourceObjectUuid());
                if (!aco.isEmpty()) {
                    continue;
                }
            } else {
                contentItemEntity = new AttributeContentItem();
                contentItemEntity.setJson(attributeContentItem);
                contentItemEntity.setAttributeDefinitionUuid(attributeDefinition.getUuid());
                contentItemEntity = attributeContentItemRepository.save(contentItemEntity);
            }

            final AttributeContent2Object objectContentItem = new AttributeContent2Object();
            objectContentItem.setObjectUuid(objectAttributeContentInfo.objectUuid());
            objectContentItem.setObjectType(objectAttributeContentInfo.objectType());
            objectContentItem.setConnectorUuid(objectAttributeContentInfo.connectorUuid());
            objectContentItem.setSourceObjectUuid(objectAttributeContentInfo.sourceObjectUuid());
            objectContentItem.setSourceObjectType(objectAttributeContentInfo.sourceObjectType());
            objectContentItem.setSourceObjectName(objectAttributeContentInfo.sourceObjectName());
            objectContentItem.setOrder(i);
            objectContentItem.setAttributeContentItem(contentItemEntity);
            attributeContent2ObjectRepository.save(objectContentItem);
        }
    }

    private List<ValidationError> validateAttributesContent(Map<String, AttributeDefinition> definitionsMapping, List<RequestAttributeDto> attributes) {
        List<ValidationError> errors = new ArrayList<>();
        for (RequestAttributeDto attribute : attributes) {
            AttributeDefinition definition = definitionsMapping.get(attribute.getName());
            if (definition == null) {
                errors.add(ValidationError.create("Content for attribute {} is provided but definition is not found", attribute.getName()));
                continue;
            }
            try {
                validateAttributeContent(definition, attribute.getContent());
            } catch (AttributeException e) {
                errors.add(ValidationError.create(e.getMessage()));
            }

            definitionsMapping.remove(attribute.getName());
        }

        // check if there are remaining required attribute definitions that are required but not set
        for (AttributeDefinition definition : definitionsMapping.values()) {
            if (Boolean.TRUE.equals(definition.isRequired())) {
                errors.add(ValidationError.create("Content for required {} attribute {} is not provided.", definition.getType().getLabel(), definition.getName()));
            }
        }

        return errors;
    }

    private void validateAttributeContent(AttributeDefinition attributeDefinition, List<BaseAttributeContent> attributeContent) throws AttributeException {
        String connectorUuidStr = attributeDefinition.getConnectorUuid() == null ? null : attributeDefinition.getConnectorUuid().toString();
        boolean noContent = attributeContent == null || attributeContent.isEmpty();

        if (Boolean.TRUE.equals(attributeDefinition.isRequired()) && noContent) {
            throw new AttributeException("Attribute is required and no content is sent", attributeDefinition.getUuid().toString(), attributeDefinition.getName(), attributeDefinition.getType(), connectorUuidStr);
        }

        // validate read only content to equal to definition content
        if (Boolean.TRUE.equals(attributeDefinition.isReadOnly())) {
            Object definitionContent = attributeDefinition.getDefinition().getContent();
            if (definitionContent == null || !definitionContent.equals(attributeContent)) {
                throw new AttributeException("Wrong value of read only attribute " + attributeDefinition.getLabel(), attributeDefinition.getUuid().toString(), attributeDefinition.getName(), attributeDefinition.getType(), connectorUuidStr);
            }
        }

        if (!noContent) {
            // check for malformed content
            for (BaseAttributeContent contentItem : attributeContent) {
                if (contentItem.getData() == null) {
                    throw new AttributeException("Attribute content is malformed and does not contain data", attributeDefinition.getUuid().toString(), attributeDefinition.getName(), attributeDefinition.getType(), connectorUuidStr);
                }
            }

            // convert content items to its respective content classes
            try {
                ATTRIBUTES_OBJECT_MAPPER.convertValue(attributeContent, ATTRIBUTES_OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, attributeDefinition.getContentType().getContentClass()));
            } catch (IllegalArgumentException e) {
                throw new AttributeException("Wrong content for attribute of content type " + attributeDefinition.getContentType().getLabel(), attributeDefinition.getUuid().toString(), attributeDefinition.getName(), attributeDefinition.getType(), connectorUuidStr);
            }
        }
    }

    public void deleteObjectAttributeContentByType(AttributeType attributeType, Resource objectType, UUID objectUuid) {
        logger.debug("Deleting the {} attributes content for {} with UUID: {}", attributeType.getLabel(), objectType.getLabel(), objectUuid);
        long deletedCount = attributeContent2ObjectRepository.deleteByAttributeContentItemAttributeDefinitionTypeAndObjectTypeAndObjectUuid(attributeType, objectType, objectUuid);
        logger.debug("Deleted {} attribute content items for {} with UUID {}", deletedCount, objectType.getLabel(), objectUuid);
    }

    private void deleteAllAttributeDefinitionContent(UUID definitionUuid) {
        long deletedCount = attributeContent2ObjectRepository.deleteByAttributeContentItemAttributeDefinitionUuid(definitionUuid);
        attributeRelationRepository.deleteByAttributeDefinitionUuid(definitionUuid);
        attributeContentItemRepository.deleteByAttributeDefinitionUuid(definitionUuid);
        logger.debug("Deleted {} attribute content items for attribute with UUID {}", deletedCount, definitionUuid);
    }

    private void deleteObjectAttributeDefinitionContent(UUID definitionUuid, Resource objectType, UUID objectUuid) {
        long deletedCount = attributeContent2ObjectRepository.deleteByObjectTypeAndObjectUuidAndAttributeContentItemAttributeDefinitionUuid(objectType, objectUuid, definitionUuid);
        logger.debug("Deleted {} attribute content items for {} with UUID {} for attribute {}", deletedCount, objectType.getLabel(), objectUuid, definitionUuid);
    }

    private SecurityResourceFilter loadCustomAttributesSecurityResourceFilter() {
        // if user is anonymous or protocol user, allow all custom attribute content for sake of system processes and protocol operations
        boolean loadAllContent = false;
        try {
            loadAllContent = AuthHelper.isLoggedProtocolUser();
        } catch (ValidationException ex) {
            // anonymous user
            // NOTE: subject to change in case of revealing custom attributes content unnecessarily
            loadAllContent = true;
        }

        return loadAllContent ? null : authHelper.loadObjectPermissions(Resource.ATTRIBUTE, ResourceAction.MEMBERS);
    }
}
