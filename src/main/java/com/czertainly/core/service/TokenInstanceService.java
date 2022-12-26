package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.cryptography.token.TokenInstanceRequestDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceDetailDto;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceDto;

import java.util.List;

public interface TokenInstanceService {
    /**
     * List of all available token instance
     *
     * @return List of available token instances {@Link TokenInstanceDto}
     */
    List<TokenInstanceDto> listTokenInstances();

    /**
     * Get the details of the token instance
     *
     * @param uuid UUID of the token instance
     * @return Details of the token instance {@Link TokenInstanceDetailDto}
     * @throws NotFoundException when the token instance is not found
     */
    TokenInstanceDetailDto getTokenInstance(String uuid) throws NotFoundException;

    /**
     * Create a new token instance on the connector
     *
     * @param request DTO containing the details for creating a new token profile {@Link TokenInstanceRequestDto}
     * @return Detail of the newly created token instance {@Link TokenInstanceDetailDto}
     * @throws AlreadyExistException when the token instance with the same data already exists
     * @throws ValidationException   When the validation of attributes or other information fails
     */
    TokenInstanceDetailDto createTokenInstance(TokenInstanceRequestDto request) throws AlreadyExistException, ValidationException;

    /**
     * Update the token instance
     *
     * @param uuid UUID of the concerned token instance
     * @param request DTO containing the details for updating token instance
     * @return Details of the updated token instance {@Link TokenInstanceDetailDto}
     * @throws NotFoundException   when the token instance is not found
     * @throws ValidationException When the validation of attributes or other information fails
     */
    TokenInstanceDetailDto updateTokenInstance(String uuid, TokenInstanceRequestDto request) throws NotFoundException, ValidationException;

    /**
     * Delete a token instance
     *
     * @param uuid UUID of the concerned token instance
     * @throws NotFoundException when the token instance is not found
     */
    void deleteTokenInstance(String uuid) throws NotFoundException;

    /**
     * Activate a token instance
     *
     * @param uuid       UUID of the concerned token instance
     * @param attributes List of attributes needed for activating the token instance
     * @throws NotFoundException when the token instance is not found
     */
    void activateTokenInstance(String uuid, List<RequestAttributeDto> attributes) throws NotFoundException;

    /**
     * Deactivate a token instance
     *
     * @param uuid UUID of the concerned token instance
     * @throws NotFoundException when the token instance is not found
     */
    void deactivateTokenInstance(String uuid) throws NotFoundException;

    /**
     * Delete the token instance
     *
     * @param uuids UUIDs of the concerned token instances
     * @throws NotFoundException when the token instance is not found
     */
    void deleteTokenInstance(List<String> uuids) throws NotFoundException;

    /**
     * Reload the status of the token instance. This method update the status of the token instance from the connector
     *
     * @param uuid UUID of the concerned token instance
     * @return Details of the token instance {@Link TokenInstanceDetailDto}
     * @throws NotFoundException when the token instance is not found
     */
    TokenInstanceDetailDto reloadStatus(String uuid) throws NotFoundException;

    /**
     * @param uuid UUID of the concerned token instance
     * @return List of Attributes needed to create the token profile
     * @throws NotFoundException when the token instance is not found
     */
    List<BaseAttribute> listTokenProfileAttributes(String uuid) throws NotFoundException;

    /**
     * @param uuid UUID of the concerned token instance
     * @return List of the attributes needed for token instance activation
     * @throws NotFoundException when the token instance is not found
     */
    List<BaseAttribute> listTokenInstanceActivationAttributes(String uuid) throws NotFoundException;
}
