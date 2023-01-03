package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.cryptography.key.KeyRequestDto;
import com.czertainly.api.model.client.cryptography.key.KeyRequestType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.cryptography.key.KeyDetailDto;
import com.czertainly.api.model.core.cryptography.key.KeyDto;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;
import java.util.Optional;

public interface CryptographicKeyService {
    /**
     * List of all available keys
     *
     * @param tokenProfileUuid UUID of the token profile
     * @param filter           Security FIlter for Access Control
     * @return List of Key details {@Link KeyDto}
     */
    List<KeyDto> listKeys(Optional<String> tokenProfileUuid, SecurityFilter filter);

    /**
     * @param tokenProfileUuid UUID of the token profile
     * @param uuid             UUID of the concerned Key
     * @return Detail of the key {@Link KeyDetailDto}
     * @throws NotFoundException  when the token profile or key is not found
     * @throws ConnectorException when there are issues with connector communication
     */
    KeyDetailDto getKey(SecuredParentUUID tokenProfileUuid, String uuid) throws NotFoundException;

    /**
     * @param request          DTO containing the information for creating a new key
     * @param tokenProfileUuid UUID of the token profile
     * @param type             Type of the key to be created
     * @return Details of the newly created key
     * @throws AlreadyExistException when the key with same data already exists
     * @throws ValidationException   when the validation of the data or attributes fails
     * @throws ConnectorException    when there are issues with connector communication
     */
    KeyDetailDto createKey(SecuredParentUUID tokenProfileUuid, KeyRequestType type, KeyRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException;

    /**
     * Destroy a key
     *
     * @param tokenProfileUuid UUID of the token profile
     * @param uuid             UUID of the concerned key
     * @param keyUuids         List of uuids that are part of the key object
     * @throws NotFoundException  when the token profile or the key uuid is not found
     * @throws ConnectorException when there are issues with connector communication
     */
    void destroyKey(SecuredParentUUID tokenProfileUuid, String uuid, List<String> keyUuids) throws ConnectorException;

    /**
     * List attributes to create a new key
     *
     * @param tokenProfileUuid UUID of the token profile
     * @param type             Type of the key to be created
     * @return List of attributes to create a new key
     * @throws NotFoundException  when the token profile is not found
     * @throws ConnectorException when there are issues with connector communication
     */
    List<BaseAttribute> listCreateKeyAttributes(SecuredUUID tokenProfileUuid, KeyRequestType type) throws ConnectorException;

    /**
     * Function to sync the list of keys from the connector
     *
     * @param tokenInstanceUuid UUID of the token instance to sync the keys
     * @throws ConnectorException
     */
    void syncKeys(SecuredUUID tokenInstanceUuid) throws ConnectorException;
}
