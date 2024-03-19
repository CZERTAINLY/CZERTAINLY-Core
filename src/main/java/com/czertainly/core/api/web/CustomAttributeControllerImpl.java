package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.CustomAttributeController;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeCreateRequestDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeDefinitionDetailDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeDefinitionDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeUpdateRequestDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.AttributeService;
import com.czertainly.core.service.ResourceService;
import com.czertainly.core.util.converter.AttributeContentTypeConverter;
import com.czertainly.core.util.converter.ResourceCodeConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
public class CustomAttributeControllerImpl implements CustomAttributeController {

    @Autowired
    private AttributeService attributeService;

    @Autowired
    private ResourceService resourceService;

    @InitBinder
    public void initBinder(final WebDataBinder webdataBinder) {
        webdataBinder.registerCustomEditor(Resource.class, new ResourceCodeConverter());
        webdataBinder.registerCustomEditor(AttributeContentType.class, new AttributeContentTypeConverter());
    }

    @Override
    public List<CustomAttributeDefinitionDto> listCustomAttributes(AttributeContentType attributeContentType) {
        return attributeService.listCustomAttributes(attributeContentType);
    }

    @Override
    public CustomAttributeDefinitionDetailDto getCustomAttribute(String uuid) throws NotFoundException {
        return attributeService.getCustomAttribute(UUID.fromString(uuid));
    }

    @Override
    public ResponseEntity<CustomAttributeDefinitionDetailDto> createCustomAttribute(CustomAttributeCreateRequestDto request) throws AlreadyExistException, NotFoundException, AttributeException {
        CustomAttributeDefinitionDetailDto definitionDetailDto = attributeService.createCustomAttribute(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(definitionDetailDto.getUuid())
                .toUri();
        return ResponseEntity.created(location).body(definitionDetailDto);
    }

    @Override
    public CustomAttributeDefinitionDetailDto editCustomAttribute(String uuid, CustomAttributeUpdateRequestDto request) throws NotFoundException, AttributeException {
        return attributeService.editCustomAttribute(UUID.fromString(uuid), request);
    }

    @Override
    public void deleteCustomAttribute(String uuid) throws NotFoundException {
        attributeService.deleteCustomAttribute(UUID.fromString(uuid));
    }

    @Override
    public void enableCustomAttribute(String uuid) throws NotFoundException {
        attributeService.enableCustomAttribute(UUID.fromString(uuid), true);
    }

    @Override
    public void disableCustomAttribute(String uuid) throws NotFoundException {
        attributeService.enableCustomAttribute(UUID.fromString(uuid), false);
    }

    @Override
    public void bulkDeleteCustomAttributes(List<String> attributeUuids) {
        attributeService.bulkDeleteCustomAttributes(attributeUuids);
    }

    @Override
    public void bulkEnableCustomAttributes(List<String> attributeUuids) {
        attributeService.bulkEnableCustomAttributes(attributeUuids, true);
    }

    @Override
    public void bulkDisableCustomAttributes(List<String> attributeUuids) {
        attributeService.bulkEnableCustomAttributes(attributeUuids, false);
    }

    @Override
    public void updateResources(String uuid, List<Resource> resources) throws NotFoundException {
        attributeService.updateResources(UUID.fromString(uuid), resources);
    }

    @Override
    public List<BaseAttribute> getResourceCustomAttributes(Resource resource) {
        return attributeService.getResourceAttributes(resource);
    }

    @Override
    public List<Resource> getResources() {
        return attributeService.getResources();
    }

    @Override
    public List<ResponseAttributeDto> updateAttributeContentForResource(
            Resource resourceName,
            String objectUuid,
            String attributeUuid,
            List<BaseAttributeContent> request
    ) throws NotFoundException, AttributeException {
        return resourceService.updateAttributeContentForObject(
                resourceName,
                SecuredUUID.fromString(objectUuid),
                UUID.fromString(attributeUuid),
                request
        );
    }

    @Override
    public List<ResponseAttributeDto> deleteAttributeContentForResource(
            Resource resourceName,
            String objectUuid,
            String attributeUuid
    ) throws NotFoundException, AttributeException {
        return resourceService.updateAttributeContentForObject(
                resourceName,
                SecuredUUID.fromString(objectUuid),
                UUID.fromString(attributeUuid),
                null
        );
    }
}
