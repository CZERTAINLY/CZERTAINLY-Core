package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.CustomAttributeController;
import com.czertainly.api.model.client.attribute.AttributeDefinitionDto;
import com.czertainly.api.model.client.attribute.CustomAttributeCreateRequestDto;
import com.czertainly.api.model.client.attribute.CustomAttributeDefinitionDetailDto;
import com.czertainly.api.model.client.attribute.CustomAttributeUpdateRequestDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AttributeService;
import com.czertainly.core.util.converter.ResourceCodeConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class CustomAttributeControllerImpl implements CustomAttributeController {

    @Autowired
    private AttributeService attributeService;

    @InitBinder
    public void initBinder(final WebDataBinder webdataBinder) {
        webdataBinder.registerCustomEditor(Resource.class, new ResourceCodeConverter());
    }


    @Override
    public List<AttributeDefinitionDto> listAttributes() {
        return attributeService.listAttributes(SecurityFilter.create(), AttributeType.CUSTOM);
    }

    @Override
    public CustomAttributeDefinitionDetailDto getAttributes(String uuid) throws NotFoundException {
        return attributeService.getAttribute(SecuredUUID.fromString(uuid));
    }

    @Override
    public ResponseEntity<CustomAttributeDefinitionDetailDto> createAttribute(CustomAttributeCreateRequestDto request) throws AlreadyExistException, NotFoundException {
        CustomAttributeDefinitionDetailDto definitionDetailDto = attributeService.createAttribute(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(definitionDetailDto.getUuid())
                .toUri();
        return ResponseEntity.created(location).body(definitionDetailDto);
    }

    @Override
    public CustomAttributeDefinitionDetailDto editAttribute(String uuid, CustomAttributeUpdateRequestDto request) throws NotFoundException {
        return attributeService.editAttribute(SecuredUUID.fromString(uuid), request);
    }

    @Override
    public void deleteAttribute(String uuid) throws NotFoundException {
        attributeService.deleteAttribute(SecuredUUID.fromString(uuid), AttributeType.CUSTOM);
    }

    @Override
    public void enableAttribute(String uuid) throws NotFoundException {
        attributeService.enableAttribute(SecuredUUID.fromString(uuid), AttributeType.CUSTOM);
    }

    @Override
    public void disableAttribute(String uuid) throws NotFoundException {
        attributeService.disableAttribute(SecuredUUID.fromString(uuid), AttributeType.CUSTOM);
    }

    @Override
    public void bulkDeleteAttributes(List<String> attributeUuids) {
        attributeService.bulkDeleteAttributes(SecuredUUID.fromList(attributeUuids), AttributeType.CUSTOM);
    }

    @Override
    public void bulkEnableAttributes(List<String> attributeUuids) {
        attributeService.bulkEnableAttributes(SecuredUUID.fromList(attributeUuids), AttributeType.CUSTOM);
    }

    @Override
    public void bulkDisableAttributes(List<String> attributeUuids) {
        attributeService.bulkDisableAttributes(SecuredUUID.fromList(attributeUuids), AttributeType.CUSTOM);
    }

    @Override
    public void updateResource(String uuid, List<Resource> resources) throws NotFoundException {
        attributeService.updateResources(SecuredUUID.fromString(uuid), resources);
    }

    @Override
    public List<BaseAttribute> getResourceAttributes(Resource resource) {
        return attributeService.getResourceAttributes(resource);
    }

    @Override
    public List<Resource> getResources() {
        return attributeService.getResources();
    }
}
