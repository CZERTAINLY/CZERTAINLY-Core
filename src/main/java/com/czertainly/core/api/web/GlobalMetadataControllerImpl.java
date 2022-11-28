package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.GlobalMetadataController;
import com.czertainly.api.model.client.attribute.AttributeDefinitionDto;
import com.czertainly.api.model.client.attribute.metadata.GlobalMetadataCreateRequestDto;
import com.czertainly.api.model.client.attribute.metadata.GlobalMetadataDefinitionDetailDto;
import com.czertainly.api.model.client.attribute.metadata.GlobalMetadataUpdateRequestDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AttributeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class GlobalMetadataControllerImpl implements GlobalMetadataController {

    @Autowired
    private AttributeService attributeService;


    @Override
    public List<AttributeDefinitionDto> listGlobalMetadata() {
        return attributeService.listAttributes(SecurityFilter.create(), AttributeType.META);
    }

    @Override
    public GlobalMetadataDefinitionDetailDto getGlobalMetadata(String uuid) throws NotFoundException {
        return attributeService.getGlobalMetadata(SecuredUUID.fromString(uuid));
    }

    @Override
    public ResponseEntity<GlobalMetadataDefinitionDetailDto> createGlobalMetadata(GlobalMetadataCreateRequestDto request) throws AlreadyExistException, NotFoundException {
        GlobalMetadataDefinitionDetailDto definitionDetailDto = attributeService.createGlobalMetadata(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(definitionDetailDto.getUuid())
                .toUri();
        return ResponseEntity.created(location).body(definitionDetailDto);
    }

    @Override
    public GlobalMetadataDefinitionDetailDto editGlobalMetadata(String uuid, GlobalMetadataUpdateRequestDto request) throws NotFoundException {
        return attributeService.editGlobalMetadata(SecuredUUID.fromString(uuid), request);
    }

    @Override
    public void deleteGlobalMetadata(String uuid) throws NotFoundException {
        attributeService.deleteAttribute(SecuredUUID.fromString(uuid), AttributeType.META);
    }

    @Override
    public void bulkDeleteGlobalMetadata(List<String> metadataUuids) {
        attributeService.bulkDeleteAttributes(SecuredUUID.fromList(metadataUuids), AttributeType.META);
    }
}
