package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.cryptography.token.TokenInstanceRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceDetailDto;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface TokenInstanceService {
    /**
     * List of all available token instance
     *
     * @param filter Security Filter for Access Control
     * @return List of available token instances {@Link TokenInstanceDto}
     */
    List<TokenInstanceDto> listTokenInstances(SecurityFilter filter);

    /**
     * Get the details of the token instance
     *
     * @param uuid UUID of the token instance
     * @return Details of the token instance {@Link TokenInstanceDetailDto}
     * @throws NotFoundException  when the token instance is not found
     * @throws ConnectorException when there are issues with connector communication or error from connector
     */
    TokenInstanceDetailDto getTokenInstance(SecuredUUID uuid) throws ConnectorException;

    /**
     * Create a new token instance on the connector
     *
     * @param request DTO containing the details for creating a new token profile {@Link TokenInstanceRequestDto}
     * @return Detail of the newly created token instance {@Link TokenInstanceDetailDto}
     * @throws AlreadyExistException when the token instance with the same data already exists
     * @throws ValidationException   When the validation of attributes or other information fails
     * @throws ConnectorException    when there are issues with connector communication or error from connector
     */
    TokenInstanceDetailDto createTokenInstance(TokenInstanceRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException;

    /**
     * Update the token instance
     *
     * @param uuid    UUID of the concerned token instance
     * @param request DTO containing the details for updating token instance
     * @return Details of the updated token instance {@Link TokenInstanceDetailDto}
     * @throws NotFoundException   when the token instance is not found
     * @throws ValidationException When the validation of attributes or other information fails
     * @throws ConnectorException  when there are issues with connector communication or error from connector
     */
    TokenInstanceDetailDto updateTokenInstance(SecuredUUID uuid, TokenInstanceRequestDto request) throws ConnectorException, ValidationException;

    /**
     * Delete a token instance
     *
     * @param uuid UUID of the concerned token instance
     * @throws NotFoundException  when the token instance is not found
     * @throws ConnectorException when there are issues with connector communication or error from connector
     */
    void deleteTokenInstance(SecuredUUID uuid) throws NotFoundException;

    /**
     * Activate a token instance
     *
     * @param uuid       UUID of the concerned token instance
     * @param attributes List of attributes needed for activating the token instance
     * @throws NotFoundException  when the token instance is not found
     * @throws ConnectorException when there are issues with connector communication or error from connector
     */
    void activateTokenInstance(SecuredUUID uuid, List<RequestAttributeDto> attributes) throws ConnectorException;

    /**
     * Deactivate a token instance
     *
     * @param uuid UUID of the concerned token instance
     * @throws NotFoundException  when the token instance is not found
     * @throws ConnectorException when there are issues with connector communication or error from connector
     */
    void deactivateTokenInstance(SecuredUUID uuid) throws ConnectorException;

    /**
     * Delete the token instance
     *
     * @param uuids UUIDs of the concerned token instances
     */
    void deleteTokenInstance(List<SecuredUUID> uuids);

    /**
     * Reload the status of the token instance. This method update the status of the token instance from the connector
     *
     * @param uuid UUID of the concerned token instance
     * @return Details of the token instance {@Link TokenInstanceDetailDto}
     * @throws NotFoundException  when the token instance is not found
     * @throws ConnectorException when there are issues with connector communication or error from connector
     */
    TokenInstanceDetailDto reloadStatus(SecuredUUID uuid) throws ConnectorException;

    /**
     * @param uuid UUID of the concerned token instance
     * @return List of Attributes needed to create the token profile
     * @throws NotFoundException  when the token instance is not found
     * @throws ConnectorException when there are issues with connector communication or error from connector
     */
    List<BaseAttribute> listTokenProfileAttributes(SecuredUUID uuid) throws ConnectorException;

    /**
     * Validate the token Profile attributes
     * @param uuid UUID of the token instance
     * @param attributes attributes to be validated
     * @throws ConnectorException when there are issues with the communication
     */
    void validateTokenProfileAttributes(SecuredUUID uuid, List<RequestAttributeDto> attributes) throws ConnectorException;

    /**
     * @param uuid UUID of the concerned token instance
     * @return List of the attributes needed for token instance activation
     * @throws NotFoundException  when the token instance is not found
     * @throws ConnectorException when there are issues with connector communication or error from connector
     */
    List<BaseAttribute> listTokenInstanceActivationAttributes(SecuredUUID uuid) throws ConnectorException;

    /**
     * Function to get the list of name and uuid dto for the objects available in the database.
     * @return List of NameAndUuidDto
     */
    List<NameAndUuidDto> listResourceObjects(SecurityFilter filter);
}
