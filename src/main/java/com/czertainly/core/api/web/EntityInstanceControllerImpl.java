package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.interfaces.core.web.EntityInstanceController;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.certificate.EntityInstanceResponseDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.entity.EntityInstanceRequestDto;
import com.czertainly.api.model.client.entity.EntityInstanceUpdateRequestDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.entity.EntityInstanceDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.EntityInstanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class EntityInstanceControllerImpl implements EntityInstanceController {

    @Autowired
    public void setEntityInstanceService(EntityInstanceService entityInstanceService) {
        this.entityInstanceService = entityInstanceService;
    }

    private EntityInstanceService entityInstanceService;

    @Override
    @AuthEndpoint(resourceName = Resource.ENTITY)
    public EntityInstanceResponseDto listEntityInstances(final SearchRequestDto requestDto) {
        return entityInstanceService.listEntityInstances(SecurityFilter.create(), requestDto);
    }

    @Override
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return entityInstanceService.getSearchableFieldInformationByGroup();
    }

    @Override
    public EntityInstanceDto getEntityInstance(String entityUuid) throws ConnectorException {
        return entityInstanceService.getEntityInstance(SecuredUUID.fromString(entityUuid));
    }

    @Override
    public ResponseEntity<?> createEntityInstance(EntityInstanceRequestDto request) throws AlreadyExistException, ConnectorException {
        EntityInstanceDto entityInstance = entityInstanceService.createEntityInstance(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{entityUuid}")
                .buildAndExpand(entityInstance.getUuid())
                .toUri();
        UuidDto dto = new UuidDto();
        dto.setUuid(entityInstance.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    public EntityInstanceDto editEntityInstance(String entityUuid, EntityInstanceUpdateRequestDto request) throws ConnectorException {
        return entityInstanceService.editEntityInstance(SecuredUUID.fromString(entityUuid), request);
    }

    @Override
    public void deleteEntityInstance(String entityUuid) throws ConnectorException {
        entityInstanceService.deleteEntityInstance(SecuredUUID.fromString(entityUuid));
    }

    @Override
    public List<BaseAttribute> listLocationAttributes(String entityUuid) throws ConnectorException {
        return entityInstanceService.listLocationAttributes(SecuredUUID.fromString(entityUuid));
    }

    @Override
    public void validateLocationAttributes(String entityUuid, List<RequestAttributeDto> attributes) throws ConnectorException {
        entityInstanceService.validateLocationAttributes(SecuredUUID.fromString(entityUuid), attributes);
    }
}
