package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.model.client.entity.EntityInstanceUpdateRequestDto;
import com.czertainly.api.model.common.AttributeDefinition;
import com.czertainly.api.model.common.RequestAttributeDto;
import com.czertainly.api.model.core.entity.EntityInstanceDto;

import java.util.List;

public interface EntityInstanceService {

    List<EntityInstanceDto> listEntityInstances();

    EntityInstanceDto getEntityInstance(String entityUuid) throws ConnectorException;

    EntityInstanceDto createEntityInstance(com.czertainly.api.model.client.entity.EntityInstanceRequestDto request) throws AlreadyExistException, ConnectorException;

    EntityInstanceDto updateEntityInstance(String entityUuid, EntityInstanceUpdateRequestDto request) throws ConnectorException;

    void removeEntityInstance(String entityUuid) throws ConnectorException;

    List<AttributeDefinition> listLocationAttributes(String entityUuid) throws ConnectorException;

    Boolean validateLocationAttributes(String entityUuid, List<RequestAttributeDto> attributes) throws ConnectorException;
}
