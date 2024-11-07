package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
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
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.logging.LogResource;
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
    @AuditLogged(module = Module.ENTITIES, resource = Resource.ENTITY, operation = Operation.LIST)
    public EntityInstanceResponseDto listEntityInstances(final SearchRequestDto requestDto) {
        return entityInstanceService.listEntityInstances(SecurityFilter.create(), requestDto);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.SEARCH_FILTER, affiliatedResource = Resource.ENTITY, operation = Operation.LIST)
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return entityInstanceService.getSearchableFieldInformationByGroup();
    }

    @Override
    @AuditLogged(module = Module.ENTITIES, resource = Resource.ENTITY, operation = Operation.DETAIL)
    public EntityInstanceDto getEntityInstance(@LogResource(uuid = true) String entityUuid) throws ConnectorException {
        return entityInstanceService.getEntityInstance(SecuredUUID.fromString(entityUuid));
    }

    @Override
    @AuditLogged(module = Module.ENTITIES, resource = Resource.ENTITY, operation = Operation.CREATE)
    public ResponseEntity<?> createEntityInstance(EntityInstanceRequestDto request) throws AlreadyExistException, ConnectorException, AttributeException {
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
    @AuditLogged(module = Module.ENTITIES, resource = Resource.ENTITY, operation = Operation.UPDATE)
    public EntityInstanceDto editEntityInstance(@LogResource(uuid = true) String entityUuid, EntityInstanceUpdateRequestDto request) throws ConnectorException, AttributeException {
        return entityInstanceService.editEntityInstance(SecuredUUID.fromString(entityUuid), request);
    }

    @Override
    @AuditLogged(module = Module.ENTITIES, resource = Resource.ENTITY, operation = Operation.DELETE)
    public void deleteEntityInstance(@LogResource(uuid = true) String entityUuid) throws ConnectorException {
        entityInstanceService.deleteEntityInstance(SecuredUUID.fromString(entityUuid));
    }

    @Override
    @AuditLogged(module = Module.ENTITIES, resource = Resource.ATTRIBUTE, name = "location", affiliatedResource = Resource.ENTITY, operation = Operation.LIST_ATTRIBUTES)
    public List<BaseAttribute> listLocationAttributes(@LogResource(uuid = true, affiliated = true) String entityUuid) throws ConnectorException {
        return entityInstanceService.listLocationAttributes(SecuredUUID.fromString(entityUuid));
    }

    @Override
    @AuditLogged(module = Module.ENTITIES, resource = Resource.ATTRIBUTE, name = "location", affiliatedResource = Resource.ENTITY, operation = Operation.VALIDATE_ATTRIBUTES)
    public void validateLocationAttributes(@LogResource(uuid = true, affiliated = true) String entityUuid, List<RequestAttributeDto> attributes) throws ConnectorException {
        entityInstanceService.validateLocationAttributes(SecuredUUID.fromString(entityUuid), attributes);
    }
}
