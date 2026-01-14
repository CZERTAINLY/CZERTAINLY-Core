package com.czertainly.core.service.impl;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.attribute.ResponseAttribute;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.common.AttributeContent;
import com.czertainly.api.model.common.attribute.common.AttributeType;
import com.czertainly.api.model.common.attribute.common.DataAttribute;
import com.czertainly.api.model.common.attribute.common.callback.AttributeCallback;
import com.czertainly.api.model.common.attribute.common.callback.AttributeCallbackMapping;
import com.czertainly.api.model.common.attribute.common.callback.AttributeValueTarget;
import com.czertainly.api.model.common.attribute.common.callback.RequestAttributeCallback;
import com.czertainly.api.model.common.attribute.common.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.CredentialAttributeContentV2;
import com.czertainly.api.model.common.attribute.v3.content.ObjectAttributeContentV3;
import com.czertainly.api.model.common.attribute.v3.content.ResourceObjectContent;
import com.czertainly.api.model.common.attribute.v3.content.data.ResourceObjectContentData;
import com.czertainly.api.model.core.auth.AttributeResource;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceDto;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.api.model.core.other.ResourceEventDto;
import com.czertainly.api.model.core.other.ResourceObjectDto;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.enums.SearchFieldTypeEnum;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.*;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.FilterPredicatesBuilder;
import com.czertainly.core.util.SearchHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.codehaus.janino.IClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ResourceServiceImpl implements ResourceService {
    private static final Logger logger = LoggerFactory.getLogger(ResourceServiceImpl.class);

    private AttributeEngine attributeEngine;

    private Map<String, ResourceExtensionService> resourceExtensionServices;

    private Map<String, AttributeResourceService> attributeResourceServices;

    @Lazy
    @Autowired
    public void setAttributeResourceServices(Map<String, AttributeResourceService> attributeResourceServices) {
        this.attributeResourceServices = attributeResourceServices;
    }

    @Lazy
    @Autowired
    public void setResourceExtensionServices(Map<String, ResourceExtensionService> resourceExtensionServices) {
        this.resourceExtensionServices = resourceExtensionServices;
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Override
    public List<ResourceDto> listResources() {
        List<ResourceDto> resources = new ArrayList<>();

        for (Resource resource : Resource.values()) {
            if (resource == Resource.NONE) {
                continue;
            }

            ResourceDto resourceDto = new ResourceDto();
            resourceDto.setResource(resource);
            resourceDto.setHasObjectAccess(resource.hasObjectAccess());
            resourceDto.setHasCustomAttributes(resource.hasCustomAttributes());
            resourceDto.setHasGroups(resource.hasGroups());
            resourceDto.setHasOwner(resource.hasOwner());
            resourceDto.setHasEvents(!ResourceEvent.listEventsByResource(resource).isEmpty());
            resourceDto.setHasRuleEvaluator(ResourceEvent.isResourceOfEvent(resource));
            resourceDto.setComplianceSubject(resource.complianceSubject());
            resourceDto.setHasComplianceProfiles(resource.hasComplianceProfiles());
            resources.add(resourceDto);
        }

        return resources;
    }

    // used only internally, not directly through API
    @Override
    public ResourceObjectDto getResourceObject(Resource resource, UUID objectUuid) throws NotFoundException {
        ResourceExtensionService resourceExtensionService = resourceExtensionServices.get(resource.getCode());
        if (resourceExtensionService == null) {
            throw new NotSupportedException("Cannot retrieve object for requested resource: " + resource.getLabel());
        }

        NameAndUuidDto nameAndUuidDto = resourceExtensionService.getResourceObject(objectUuid);
        return new ResourceObjectDto(resource, objectUuid, nameAndUuidDto.getName());
    }

    @Override
    public List<NameAndUuidDto> getResourceObjects(Resource resource, List<SearchFilterRequestDto> filters, PaginationRequestDto pagination) throws NotSupportedException {
        ResourceExtensionService resourceExtensionService = resourceExtensionServices.get(resource.getCode());
        if (resourceExtensionService == null)
            throw new NotSupportedException("Cannot list objects for requested resource: " + resource.getLabel());
        return resourceExtensionService.listResourceObjects(SecurityFilter.create(), filters, pagination);
    }

    @Override
    public List<ResponseAttribute> updateAttributeContentForObject(Resource resource, SecuredUUID objectUuid, UUID attributeUuid, List<? extends AttributeContent> attributeContentItems) throws NotFoundException, AttributeException {
        logger.info("Updating the attribute {} for resource {} with value {}", attributeUuid, resource, attributeUuid);
        ResourceExtensionService resourceExtensionService = resourceExtensionServices.get(resource.getCode());
        if (!resource.hasCustomAttributes() || resourceExtensionService == null)
            throw new NotSupportedException("Cannot update custom attribute for requested resource: " + resource.getCode());
        resourceExtensionService.evaluatePermissionChain(objectUuid);

        attributeEngine.updateObjectCustomAttributeContent(resource, objectUuid.getValue(), attributeUuid, null, attributeContentItems);
        return attributeEngine.getObjectCustomAttributesContent(resource, objectUuid.getValue());
    }

    @Override
    public List<SearchFieldDataByGroupDto> listResourceRuleFilterFields(Resource resource, boolean settable) throws NotFoundException {

        List<SearchFieldDataByGroupDto> searchFieldDataByGroupDtos = attributeEngine.getResourceSearchableFields(resource, settable);
        List<FilterField> filterFields = FilterField.getEnumsForResource(resource);
        if (filterFields.isEmpty() && searchFieldDataByGroupDtos.isEmpty()) return List.of();
        List<SearchFieldDataDto> fieldDataDtos = new ArrayList<>();
        for (FilterField filterField : filterFields) {
            // skip filter fields with JSON paths since it is not supported by rule evaluator
            // If getting only settable fields, skip not settable fields
            if (filterField.getJsonPath() != null || (settable && !filterField.isSettable())) {
                continue;
            }
            // Filter field has a single value, don't need to provide list
            if (filterField.getType() != SearchFieldTypeEnum.LIST)
                fieldDataDtos.add(SearchHelper.prepareSearch(filterField));
            else {
                // Filter field has values of an Enum
                if (filterField.getEnumClass() != null)
                    fieldDataDtos.add(SearchHelper.prepareSearch(filterField, filterField.getEnumClass().getEnumConstants()));
                    // Filter field has values of all objects of another entity
                else if (filterField.getFieldResource() != null)
                    fieldDataDtos.add(SearchHelper.prepareSearch(filterField, getResourceObjects(filterField.getFieldResource(), null, null)));
                    // Filter field has values of all possible values of a property
                else {
                    fieldDataDtos.add(SearchHelper.prepareSearch(filterField, FilterPredicatesBuilder.getAllValuesOfProperty(FilterPredicatesBuilder.buildPathToProperty(filterField.getJoinAttributes(), filterField.getFieldAttribute()), resource, entityManager).getResultList()));
                }
            }
        }

        if (!fieldDataDtos.isEmpty())
            searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(fieldDataDtos, FilterFieldSource.PROPERTY));

        return searchFieldDataByGroupDtos;
    }

    @Override
    public List<ResourceEventDto> listResourceEvents(Resource resource) {
        return ResourceEvent.listEventsByResource(resource).stream().map(ResourceEventDto::new).toList();
    }

    @Override
    public Map<ResourceEvent, List<ResourceEventDto>> listAllResourceEvents() {
        return Arrays.stream(ResourceEvent.values())
                .collect(Collectors.groupingBy(
                        event -> event,
                        Collectors.mapping(ResourceEventDto::new, Collectors.toList())
                ));
    }

    @Override
    public boolean hasResourceExtensionService(Resource resource) {
        return resourceExtensionServices.keySet().stream().anyMatch(key -> key.equals(resource.getCode()));
    }

    @Override
    public void loadResourceObjectContentData(AttributeCallback callback, RequestAttributeCallback requestAttributeCallback, AttributeResource resource) throws NotFoundException, AttributeException {
        if (callback == null) {
            logger.warn("Given Callback is null");
            return;
        }

        if (callback.getMappings() != null) {
            for (AttributeCallbackMapping mapping : callback.getMappings()) {
                if (AttributeContentType.RESOURCE == mapping.getAttributeContentType()) {
                    for (AttributeValueTarget target : mapping.getTargets()) {
                        if (target != AttributeValueTarget.BODY) {
                            logger.warn("Illegal 'from' Attribute type {} for target {}",
                                    mapping.getAttributeType(), target);
                            continue;
                        }
                        Serializable bodyKeyValue = requestAttributeCallback.getBody().get(mapping.getTo());
                        NameAndUuidDto resourceId = getResourceId(bodyKeyValue);
                        String resourceUuid = resourceId.getUuid();
                        ResourceObjectContentData data = getResourceObjectContentData(resource, UUID.fromString(resourceUuid), resourceId.getName());
                        requestAttributeCallback.getBody().put(mapping.getTo(), data);
                    }
                }
            }
        }
    }


    private static NameAndUuidDto getResourceId(Serializable bodyKeyValue) {
        switch (bodyKeyValue) {
            case NameAndUuidDto nameAndUuidDto -> {
                return nameAndUuidDto;
            }
            case ResourceObjectContent resourceObjectContent -> {
                return new NameAndUuidDto(resourceObjectContent.getData().getName(), resourceObjectContent.getData().getUuid());
            }
            case List<?> list when list.getFirst() instanceof ResourceObjectContent resourceObjectContent -> {
                return new NameAndUuidDto(resourceObjectContent.getData().getName(), resourceObjectContent.getData().getUuid());
            }
            case Map<?, ?> map -> {
                if (map.containsKey("uuid") && map.containsKey("name")) {
                    return new NameAndUuidDto(map.get("uuid").toString(), map.get("name").toString());
                } else {
                    try {
                        Map<?, ?> data = (Map<?, ?>) (new ObjectMapper().convertValue(bodyKeyValue, ObjectAttributeContentV3.class)).getData();
                        String resourceUuid = data.get("uuid").toString();
                        String name = data.get("name").toString();
                        return new NameAndUuidDto(name, resourceUuid);
                    } catch (Exception e) {
                        throw new ValidationException(ValidationError.create(
                                "Invalid value {}, because of {}.", bodyKeyValue, e.getMessage()));
                    }
                }
            }
            case null, default -> throw new ValidationException(ValidationError.create(
                    "Invalid value {}. Instance of {} is expected.", bodyKeyValue, NameAndUuidDto.class));
        }
    }

    @Override
    public void loadResourceObjectContentData(List<DataAttribute> attributes) throws NotFoundException, AttributeException {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }

        for (DataAttribute attribute : attributes) {
            if (!AttributeContentType.RESOURCE.equals(attribute.getContentType())) {
                continue;
            }
            NameAndUuidDto resourceId = AttributeDefinitionUtils.getNameAndUuidData(attribute.getName(), AttributeDefinitionUtils.getClientAttributes(attributes));
            if (resourceId == null || resourceId.getUuid() == null)
                throw new AttributeException("UUID of Resource Object is missing.", attribute.getUuid(), attribute.getName(), AttributeType.DATA, "");
            ResourceObjectContentData data = getResourceObjectContentData(attribute.getProperties().getResource(), UUID.fromString(resourceId.getUuid()), resourceId.getName());
            attribute.setContent(List.of(new ResourceObjectContent(resourceId.getName(), data)));
        }
    }

    private ResourceObjectContentData getResourceObjectContentData(AttributeResource resource, UUID uuid, String name) throws NotFoundException, AttributeException {
        ResourceObjectContentData data = new ResourceObjectContentData();
        if (resource.isHasContent())
            data.setBase64Content(attributeResourceServices.get(resource.getCode()).getResourceObjectContent(uuid));
        data.setAttributes(attributeEngine.getObjectDataAttributesContent(Resource.findByCode(resource.getCode()), uuid));
        data.setResource(resource);
        data.setUuid(uuid.toString());
        data.setName(name);
        return data;
    }


}
