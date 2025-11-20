package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.NotSupportedException;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContentV2;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceDto;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.api.model.core.other.ResourceEventDto;
import com.czertainly.api.model.core.other.ResourceObjectDto;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.enums.SearchFieldTypeEnum;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.*;
import com.czertainly.core.util.FilterPredicatesBuilder;
import com.czertainly.core.util.SearchHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ResourceServiceImpl implements ResourceService {
    private static final Logger logger = LoggerFactory.getLogger(ResourceServiceImpl.class);

    private AttributeEngine attributeEngine;

    private Map<String, ResourceExtensionService> resourceExtensionServices;

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
    public List<NameAndUuidDto> getResourceObjects(Resource resource) throws NotSupportedException {
        ResourceExtensionService resourceExtensionService = resourceExtensionServices.get(resource.getCode());
        if (resourceExtensionService == null)
            throw new NotSupportedException("Cannot list objects for requested resource: " + resource.getLabel());
        return resourceExtensionService.listResourceObjects(SecurityFilter.create());
    }

    @Override
    public List<ResponseAttributeDto> updateAttributeContentForObject(Resource resource, SecuredUUID objectUuid, UUID attributeUuid, List<BaseAttributeContentV2> attributeContentItems) throws NotFoundException, AttributeException {
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
                    fieldDataDtos.add(SearchHelper.prepareSearch(filterField, getResourceObjects(filterField.getFieldResource())));
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

}
