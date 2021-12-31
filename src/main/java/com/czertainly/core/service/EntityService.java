package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.certificate.entity.EntityDto;
import com.czertainly.api.model.core.certificate.entity.EntityRequestDto;

import java.util.List;

public interface EntityService {

    List<EntityDto> listEntity();
    EntityDto getCertificateEntity(String uuid) throws NotFoundException;

    EntityDto createEntity(EntityRequestDto request) throws ValidationException, AlreadyExistException;
    EntityDto updateEntity(String uuid, EntityRequestDto request) throws NotFoundException;

    void removeEntity(String uuid) throws NotFoundException;
    void bulkRemoveEntity(List<String> entityUuids);
}
