package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.EntityController;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.certificate.entity.EntityDto;
import com.czertainly.api.model.core.certificate.entity.EntityRequestDto;
import com.czertainly.core.service.EntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class EntityControllerImpl implements EntityController {

    @Autowired
    private EntityService entityService;

    @Override
    public List<EntityDto> listEntities() {
        return entityService.listEntity();
    }

    @Override
    public EntityDto getEntity(@PathVariable String uuid) throws NotFoundException {
        return entityService.getCertificateEntity(uuid);
    }

    @Override
    public ResponseEntity<?> createEntity(@RequestBody EntityRequestDto request) throws AlreadyExistException, NotFoundException {
        EntityDto entityDto = entityService.createEntity(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(entityDto.getUuid())
                .toUri();

        UuidDto dto = new UuidDto();
        dto.setUuid(entityDto.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    public EntityDto updateEntity(@PathVariable String uuid, @RequestBody EntityRequestDto request) throws NotFoundException {
        return entityService.updateEntity(uuid, request);
    }

    @Override
    public void removeEntity(@PathVariable String uuid) throws NotFoundException {
        entityService.removeEntity(uuid);
    }

    @Override
    public void bulkRemoveEntity(List<String> entityUuids) throws NotFoundException {
        entityService.bulkRemoveEntity(entityUuids);
    }
}
