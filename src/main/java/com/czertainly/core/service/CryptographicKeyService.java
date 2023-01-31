package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.cryptography.key.*;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.cryptography.key.KeyDetailDto;
import com.czertainly.api.model.core.cryptography.key.KeyDto;
import com.czertainly.api.model.core.cryptography.key.KeyEventHistoryDto;
import com.czertainly.api.model.core.cryptography.key.KeyItemDto;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CryptographicKeyService extends ResourceExtensionService  {
    /**
     * List of all available keys
     *
     * @param tokenInstanceUuid UUID of the token instance
     * @param filter            Security Filter for Access Control
     * @return List of Key details {@Link KeyDto}
     */
    List<KeyDto> listKeys(Optional<String> tokenInstanceUuid, SecurityFilter filter);

    /**
     * @param tokenInstanceUuid UUID of the token instance
     * @param uuid              UUID of the concerned Key
     * @return Detail of the key {@Link KeyDetailDto}
     * @throws NotFoundException  when the token profile or key is not found
     * @throws ConnectorException when there are issues with connector communication
     */
    KeyDetailDto getKey(SecuredParentUUID tokenInstanceUuid, String uuid) throws NotFoundException;

    /**
     * Get the detail of the key item
     * @param tokenInstanceUuid UUID of the token instance
     * @param uuid UUID of the parent key object
     * @param keyItemUuid UUID of the key item
     * @return Key Item detail
     * @throws NotFoundException when the key or token instance is not found
     */
    KeyItemDto getKeyItem(SecuredParentUUID tokenInstanceUuid, String uuid, String keyItemUuid) throws  NotFoundException;

    /**
     * @param request           DTO containing the information for creating a new key
     * @param tokenInstanceUuid UUID of the token instance
     * @param type              Type of the key to be created
     * @return Details of the newly created key
     * @throws AlreadyExistException when the key with same data already exists
     * @throws ValidationException   when the validation of the data or attributes fails
     * @throws ConnectorException    when there are issues with connector communication
     */
    KeyDetailDto createKey(
            UUID tokenInstanceUuid,
            SecuredParentUUID tokenProfileUuid,
            KeyRequestType type,
            KeyRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException;

    /**
     * Function to update the key details
     *
     * @param tokenInstanceUuid UUID of the token instance
     * @param uuid              UUID of the key
     * @param request           Information regarding the update key
     * @return Updated token Instance details
     */
    KeyDetailDto editKey(
            SecuredParentUUID tokenInstanceUuid,
            UUID uuid,
            EditKeyRequestDto request
    ) throws NotFoundException;

    /**
     * Function to disable a key
     *
     * @param tokenInstanceUuid UUID of the token instance
     * @param uuid              UUID of the key
     * @throws NotFoundException   when the key is not found
     * @throws ValidationException when the key is already disabled
     */
    void disableKey(
            SecuredParentUUID tokenInstanceUuid,
            UUID uuid,
            List<String> keyUuids
    ) throws NotFoundException, ValidationException;

    /**
     * Function to enable a disabled key
     *
     * @param tokenInstanceUuid UUID of the token instance
     * @param uuid              UUID of the key
     * @throws NotFoundException   when the key with given uuid is not found
     * @throws ValidationException when the key is already active
     */
    void enableKey(
            SecuredParentUUID tokenInstanceUuid,
            UUID uuid,
            List<String> keyUuids
    ) throws NotFoundException, ValidationException;

    /**
     * Function to disable multiple keys
     *
     * @param uuids UUIDs of the keys
     */
    void disableKey(
            List<String> uuids
    );

    /**
     * Function to enable multiple keys
     *
     * @param uuids UUIDs of the keys
     */
    void enableKey(
            List<String> uuids
    );

    /**
     * Function to enable multiple key items
     *
     * @param uuids UUIDs of the key items
     */
    void enableKeyItems(List<String> uuids);

    /**
     * Function to disable multiple key items
     *
     * @param uuids UUIDs of the key items
     */
    void disableKeyItems(List<String> uuids);

    /**
     * Function to delete the key
     *
     * @param tokenInstanceUuid UUID of the token instance
     * @param uuid              UUID of the key
     * @param keyUuids          UUIDs of the items inside the key. If empty is provided, all the items will be deleted
     * @throws NotFoundException
     */
    void deleteKey(
            SecuredParentUUID tokenInstanceUuid,
            UUID uuid,
            List<String> keyUuids
    ) throws NotFoundException;

    /**
     * Function to delete multiple key
     *
     * @param uuids Key UUIDs
     * @throws NotFoundException
     */
    void deleteKey(
            List<String> uuids
    ) throws NotFoundException;

    /**
     * Function to delete multiple key items
     *
     * @param keyItemUuids Key Item UUIDs
     * @throws ConnectorException
     */
    void deleteKeyItems(List<String> keyItemUuids) throws ConnectorException;

    /**
     * Destroy a key
     *
     * @param tokenInstanceUuid UUID of the token instance
     * @param uuid              UUID of the concerned key
     * @param keyUuids          List of uuids that are part of the key object
     * @throws NotFoundException  when the token profile or the key uuid is not found
     * @throws ConnectorException when there are issues with connector communication
     */
    void destroyKey(SecuredParentUUID tokenInstanceUuid, String uuid, List<String> keyUuids) throws ConnectorException;

    /**
     * Destroy multiple keys
     *
     * @param uuids UUID of the concerned keys
     * @throws NotFoundException  when the token profile or the key uuid is not found
     * @throws ConnectorException when there are issues with connector communication
     */
    void destroyKey(List<String> uuids) throws ConnectorException;

    /**
     * Destroy multiple keys
     *
     * @param keyItemUuids UUID of the concerned key items
     * @throws NotFoundException  when the token profile or the key uuid is not found
     * @throws ConnectorException when there are issues with connector communication
     */
    void destroyKeyItems(List<String> keyItemUuids) throws ConnectorException;

    /**
     * List attributes to create a new key
     *
     * @param tokenInstanceUuid UUID of the token instance
     * @param type              Type of the key to be created
     * @return List of attributes to create a new key
     * @throws NotFoundException  when the token profile is not found
     * @throws ConnectorException when there are issues with connector communication
     */
    List<BaseAttribute> listCreateKeyAttributes(
            UUID tokenInstanceUuid,
            SecuredParentUUID tokenProfileUuid,
            KeyRequestType type) throws ConnectorException;

    /**
     * Function to sync the list of keys from the connector
     *
     * @param tokenInstanceUuid UUID of the token instance to sync the keys
     * @throws ConnectorException
     */
    void syncKeys(SecuredParentUUID tokenInstanceUuid) throws ConnectorException;

    /**
     * Function to mark the key as compromised
     *
     * @param tokenInstanceUuid UUID of the token instance
     * @param uuid              UUID of the key
     * @param request          UUIDs of the sub items inside the key. If empty list is provided
     *                          then all the items inside the key will be marked as compromised
     */
    void compromiseKey(SecuredParentUUID tokenInstanceUuid, UUID uuid, CompromiseKeyRequestDto request) throws NotFoundException;

    /**
     * Function to mark the keys as compromised
     *
     * @param request UUIDs of the key
     */
    void compromiseKey(BulkCompromiseKeyRequestDto request);

    /**
     * Function to mark the key items as compromised
     *
     * @param request UUIDs of the key items
     */
    void compromiseKeyItems(BulkCompromiseKeyItemRequestDto request);

    /**
     * Function to update the usages for the key
     *
     * @param request Request containing the details for updating the usages
     */
    void updateKeyUsages(BulkKeyUsageRequestDto request);

    /**
     * Update the key usages for multiple keys and its items
     *
     * @param tokenInstanceUuid UUID of the token instance
     * @param uuid              UUID of the key
     * @param request           Request containing the details for the key usage updates
     */
    void updateKeyUsages(SecuredParentUUID tokenInstanceUuid, UUID uuid, UpdateKeyUsageRequestDto request) throws NotFoundException;

    /**
     * Function to update the usages for the key items
     *
     * @param request Request containing the details for updating the usages
     */
    void updateKeyItemUsages(BulkKeyItemUsageRequestDto request);

    /**
     * Get the list of actions and events done of the provided key item
     *
     * @param tokenInstanceUuid UUID of the token Instance
     * @param uuid              Key UUID
     * @param keyItemUuid       UUID of the key Item
     * @return
     */
    List<KeyEventHistoryDto> getEventHistory(SecuredParentUUID tokenInstanceUuid, UUID uuid, UUID keyItemUuid) throws NotFoundException;

    /**
     * Function to get the key based on the sha 256 key fingerprint
     *
     * @param fingerprint SHA 256 fingerprint of the key
     * @return Cryptographic Key UUID
     */
    UUID findKeyByFingerprint(String fingerprint);
}
