package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.interfaces.core.web.EntityInstanceController;
import com.czertainly.api.model.client.entity.EntityInstanceRequestDto;
import com.czertainly.api.model.client.entity.EntityInstanceUpdateRequestDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.common.attribute.RequestAttributeDto;
import com.czertainly.api.model.core.entity.EntityInstanceDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.service.EntityInstanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@Secured({"ROLE_ADMINISTRATOR", "ROLE_SUPERADMINISTRATOR"})
public class EntityInstanceControllerImpl implements EntityInstanceController {

    @Autowired
    public void setEntityInstanceService(EntityInstanceService entityInstanceService) {
        this.entityInstanceService = entityInstanceService;
    }

    private EntityInstanceService entityInstanceService;

    @Override
    @AuthEndpoint(resourceName = Resource.ENTITY, actionName = ResourceAction.LIST, isListingEndPoint = true)
    public List<EntityInstanceDto> listEntityInstances() {
        return entityInstanceService.listEntityInstances();
    }

    @Override
    @AuthEndpoint(resourceName = Resource.ENTITY, actionName = ResourceAction.DETAIL)
    public EntityInstanceDto getEntityInstance(String entityUuid) throws ConnectorException {
        return entityInstanceService.getEntityInstance(entityUuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.ENTITY, actionName = ResourceAction.CREATE)
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
    @AuthEndpoint(resourceName = Resource.ENTITY, actionName = ResourceAction.UPDATE)
    public EntityInstanceDto editEntityInstance(String entityUuid, EntityInstanceUpdateRequestDto request) throws ConnectorException {
        return entityInstanceService.editEntityInstance(entityUuid, request);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.ENTITY, actionName = ResourceAction.DELETE)
    public void deleteEntityInstance(String entityUuid) throws ConnectorException {
        entityInstanceService.deleteEntityInstance(entityUuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.LOCATION, actionName = ResourceAction.NONE)
    public List<AttributeDefinition> listLocationAttributes(String entityUuid) throws ConnectorException {
        return entityInstanceService.listLocationAttributes(entityUuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.LOCATION, actionName = ResourceAction.NONE)
    public void validateLocationAttributes(String entityUuid, List<RequestAttributeDto> attributes) throws ConnectorException {
        entityInstanceService.validateLocationAttributes(entityUuid, attributes);
    }
}
