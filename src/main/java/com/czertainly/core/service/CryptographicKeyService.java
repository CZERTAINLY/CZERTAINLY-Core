package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.cryptography.key.KeyRequestDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.cryptography.key.KeyDetailDto;
import com.czertainly.api.model.core.cryptography.key.KeyDto;

import java.util.List;
import java.util.Optional;

public interface CryptographicKeyService {
    /**
     * LIst of all available keys
     *
     * @param tokenInstanceUuid UUID of the token instance
     * @return List of Key details {@Link KeyDto}
     */
    List<KeyDto> listKeys(Optional<String> tokenInstanceUuid);

    /**
     * @param tokenInstanceUuid UUID of the token instance
     * @param uuid              UUID of the concerned Key
     * @return Detail of the key {@Link KeyDetailDto}
     * @throws NotFoundException when the token instance or key is not found
     */
    KeyDetailDto getKey(String tokenInstanceUuid, String uuid) throws NotFoundException;

    /**
     * @param request DTO containing the information for creating a new key
     * @param tokenInstanceUuid UUID of the token instance
     * @return Details of the newly created key
     * @throws AlreadyExistException when the key with same data already exists
     * @throws ValidationException   when the validation of the data or attributes fails
     */
    KeyDetailDto createKey(String tokenInstanceUuid, KeyRequestDto request) throws AlreadyExistException, ValidationException;

    /**
     * Destroy a key
     *
     * @param tokenInstanceUuid UUID of the Token Instance
     * @param uuid              UUID of the concerned key
     * @throws NotFoundException when the token instance or the key uuid is not found
     */
    void destroyKey(String tokenInstanceUuid, String uuid) throws NotFoundException;

    /**
     * List attributes to create a new key
     *
     * @param tokenInstanceUuid UUID of the token instance
     * @return List of attributes to create a new key
     * @throws NotFoundException when the token instance is not found
     */
    List<BaseAttribute> listCreateKeyAttributes(String tokenInstanceUuid) throws NotFoundException;
}
