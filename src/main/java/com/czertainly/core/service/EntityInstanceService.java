package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.entity.EntityInstanceUpdateRequestDto;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.common.attribute.RequestAttributeDto;
import com.czertainly.api.model.core.entity.EntityInstanceDto;

import java.util.List;

public interface EntityInstanceService {

    /**
     * List available Entity instances
     * @return List of available Entity instances
     */
    List<EntityInstanceDto> listEntityInstances();

    /**
     * Get Entity instance by UUID
     * @param entityUuid UUID of Entity instance
     * @return Entity instance
     * @throws NotFoundException when Entity instance with given UUID is not found
     * @throws ConnectorException when failed to get Entity instance information
     */
    EntityInstanceDto getEntityInstance(String entityUuid) throws NotFoundException, ConnectorException;

    /**
     * Create Entity instance
     * @param entityInstanceRequestDto Request to create Entity instance, see {@link EntityInstanceUpdateRequestDto}
     * @return Created Entity instance
     * @throws AlreadyExistException when Entity instance already exists
     * @throws ConnectorException when failed to create Entity instance
     */
    EntityInstanceDto createEntityInstance(com.czertainly.api.model.client.entity.EntityInstanceRequestDto entityInstanceRequestDto) throws AlreadyExistException, ConnectorException;

    /**
     * Update Entity instance
     * @param entityUuid UUID of Entity instance
     * @param entityInstanceUpdateRequestDto Request to update Entity instance, see {@link EntityInstanceUpdateRequestDto}
     * @return Updated Entity instance
     * @throws NotFoundException when Entity instance with given UUID is not found
     * @throws ConnectorException when failed to update Entity instance
     */
    EntityInstanceDto updateEntityInstance(String entityUuid, EntityInstanceUpdateRequestDto entityInstanceUpdateRequestDto) throws NotFoundException, ConnectorException;

    /**
     * Delete Entity instance
     * @param entityUuid UUID of Entity instance
     * @throws NotFoundException when Entity instance with given UUID is not found
     * @throws ConnectorException when failed to delete Entity instance
     */
    void removeEntityInstance(String entityUuid) throws NotFoundException, ConnectorException;

    /**
     * List Location Attributes supported by  Entity instance
     * @param entityUuid UUID of Entity instance
     * @return List of Location Attributes supported by Entity instance
     * @throws NotFoundException when Entity instance with given UUID is not found
     * @throws ConnectorException when failed to get Location Attributes
     */
    List<AttributeDefinition> listLocationAttributes(String entityUuid) throws NotFoundException, ConnectorException;

    /**
     * Validate Location Attributes for Entity instance
     * @param entityUuid UUID of Entity instance
     * @param locationAttributes Request Attributes to validate, see {@link RequestAttributeDto}
     * @throws NotFoundException when Entity instance with given UUID is not found
     * @throws ConnectorException when failed to validate Location Attributes
     */
    void validateLocationAttributes(String entityUuid, List<RequestAttributeDto> locationAttributes) throws NotFoundException, ConnectorException;
}
