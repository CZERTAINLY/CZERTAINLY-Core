package com.czertainly.core.api.web;

import com.czertainly.api.exception.*;
import com.czertainly.api.interfaces.core.web.CryptographicKeyController;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.cryptography.CryptographicKeyResponseDto;
import com.czertainly.api.model.client.cryptography.key.*;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cryptography.key.KeyDetailDto;
import com.czertainly.api.model.core.cryptography.key.KeyDto;
import com.czertainly.api.model.core.cryptography.key.KeyEventHistoryDto;
import com.czertainly.api.model.core.cryptography.key.KeyItemDetailDto;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.CryptographicKeyService;
import com.czertainly.core.util.converter.KeyRequestTypeConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
public class CryptographicKeyControllerImpl implements CryptographicKeyController {

    private CryptographicKeyService cryptographicKeyService;

    @Autowired
    public void setCryptographicKeyService(CryptographicKeyService cryptographicKeyService) {
        this.cryptographicKeyService = cryptographicKeyService;
    }

    @InitBinder
    public void initBinder(final WebDataBinder webdataBinder) {
        webdataBinder.registerCustomEditor(KeyRequestType.class, new KeyRequestTypeConverter());
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY, operation = Operation.LIST)
    public CryptographicKeyResponseDto listCryptographicKeys(SearchRequestDto request) throws ValidationException {
        return cryptographicKeyService.listCryptographicKeys(SecurityFilter.create(), request);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.SEARCH_FILTER, affiliatedResource = Resource.CRYPTOGRAPHIC_KEY, operation = Operation.LIST)
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return cryptographicKeyService.getSearchableFieldInformation();
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY, operation = Operation.LIST)
    public List<KeyDto> listKeyPairs(Optional<String> tokenProfileUuid) {
        return cryptographicKeyService.listKeyPairs(tokenProfileUuid, SecurityFilter.create());
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY, affiliatedResource = Resource.TOKEN, operation = Operation.DETAIL)
    public KeyDetailDto getKey(@LogResource(uuid = true, affiliated = true) String tokenInstanceUuid, @LogResource(uuid = true) String uuid) throws NotFoundException {
        return cryptographicKeyService.getKey(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                SecuredUUID.fromString(uuid)
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY_ITEM, affiliatedResource = Resource.TOKEN, operation = Operation.DETAIL)
    public KeyItemDetailDto getKeyItem(
            @LogResource(uuid = true, affiliated = true) String tokenInstanceUuid,
            String uuid,
            @LogResource(uuid = true) String keyItemUuid
    ) throws NotFoundException {
        return cryptographicKeyService.getKeyItem(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                SecuredUUID.fromString(uuid),
                keyItemUuid
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY, affiliatedResource = Resource.TOKEN, operation = Operation.CREATE)
    public KeyDetailDto createKey(@LogResource(uuid = true, affiliated = true) String tokenInstanceUuid, String tokenProfileUuid, KeyRequestType type, KeyRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException, AttributeException {
        return cryptographicKeyService.createKey(
                UUID.fromString(tokenInstanceUuid),
                SecuredParentUUID.fromString(tokenProfileUuid),
                type,
                request
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY, affiliatedResource = Resource.TOKEN, operation = Operation.UPDATE)
    public KeyDetailDto editKey(@LogResource(uuid = true, affiliated = true) String tokenInstanceUuid, @LogResource(uuid = true) String uuid, EditKeyRequestDto request) throws ConnectorException, AttributeException {
        return cryptographicKeyService.editKey(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                SecuredUUID.fromString(uuid),
                request
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY, affiliatedResource = Resource.TOKEN, operation = Operation.SYNC)
    public void syncKeys(@LogResource(uuid = true, affiliated = true) String tokenInstanceUuid) throws ConnectorException, AttributeException {
        cryptographicKeyService.syncKeys(
                SecuredParentUUID.fromString(tokenInstanceUuid)
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY, affiliatedResource = Resource.TOKEN, operation = Operation.COMPROMISE)
    public void compromiseKey(@LogResource(uuid = true, affiliated = true) String tokenInstanceUuid, @LogResource(uuid = true) String uuid, CompromiseKeyRequestDto request) throws NotFoundException {
        cryptographicKeyService.compromiseKey(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                UUID.fromString(uuid),
                request
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY, operation = Operation.COMPROMISE)
    public void compromiseKeys(BulkCompromiseKeyRequestDto request) {
        cryptographicKeyService.compromiseKey(
                request
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY_ITEM, operation = Operation.COMPROMISE)
    public void compromiseKeyItems(BulkCompromiseKeyItemRequestDto request) {
        cryptographicKeyService.compromiseKeyItems(
                request
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY, affiliatedResource = Resource.TOKEN, operation = Operation.DESTROY)
    public void destroyKey(@LogResource(uuid = true, affiliated = true) String tokenInstanceUuid, @LogResource(uuid = true) String uuid, List<String> keyItemUuids) throws ConnectorException {
        cryptographicKeyService.destroyKey(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                uuid,
                keyItemUuids
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY, operation = Operation.DESTROY)
    public void destroyKeys(@LogResource(uuid = true) List<String> keyUuids) throws ConnectorException {
        cryptographicKeyService.destroyKey(
                keyUuids
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY_ITEM, operation = Operation.DESTROY)
    public void destroyKeyItems(@LogResource(uuid = true) List<String> keyItemUuids) throws ConnectorException {
        cryptographicKeyService.destroyKeyItems(
                keyItemUuids
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY, affiliatedResource = Resource.TOKEN, operation = Operation.DELETE)
    public void deleteKey(@LogResource(uuid = true, affiliated = true) String tokenInstanceUuid, @LogResource(uuid = true) String uuid, List<String> keyItemUuids) throws ConnectorException {
        cryptographicKeyService.deleteKey(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                UUID.fromString(uuid),
                keyItemUuids
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY, operation = Operation.DELETE)
    public void deleteKeys(@LogResource(uuid = true) List<String> keyUuids) throws ConnectorException {
        cryptographicKeyService.deleteKey(
                keyUuids
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY_ITEM, operation = Operation.DELETE)
    public void deleteKeyItems(@LogResource(uuid = true) List<String> keyItemUuids) throws ConnectorException {
        cryptographicKeyService.deleteKeyItems(
                keyItemUuids
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY, affiliatedResource = Resource.TOKEN, operation = Operation.ENABLE)
    public void enableKey(@LogResource(uuid = true, affiliated = true) String tokenInstanceUuid, @LogResource(uuid = true) String uuid, List<String> keyItemUuids) throws NotFoundException {
        cryptographicKeyService.enableKey(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                UUID.fromString(uuid),
                keyItemUuids
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY, operation = Operation.ENABLE)
    public void enableKeys(@LogResource(uuid = true) List<String> uuids) {
        cryptographicKeyService.enableKey(
                uuids
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY_ITEM, operation = Operation.ENABLE)
    public void enableKeyItems(@LogResource(uuid = true) List<String> uuids) {
        cryptographicKeyService.enableKeyItems(
                uuids
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY, affiliatedResource = Resource.TOKEN, operation = Operation.DISABLE)
    public void disableKey(@LogResource(uuid = true, affiliated = true) String tokenInstanceUuid, @LogResource(uuid = true) String uuid, List<String> keyItemUuids) throws NotFoundException {
        cryptographicKeyService.disableKey(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                UUID.fromString(uuid),
                keyItemUuids
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY, operation = Operation.DISABLE)
    public void disableKeys(@LogResource(uuid = true) List<String> uuids) {
        cryptographicKeyService.disableKey(
                uuids
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY_ITEM, operation = Operation.DISABLE)
    public void disableKeyItems(@LogResource(uuid = true) List<String> uuids) {
        cryptographicKeyService.disableKeyItems(
                uuids
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY, affiliatedResource = Resource.TOKEN, operation = Operation.UPDATE_KEY_USAGE)
    public void updateKeyUsages(@LogResource(uuid = true, affiliated = true) String tokenInstanceUuid, @LogResource(uuid = true) String uuid, UpdateKeyUsageRequestDto request) throws NotFoundException, ValidationException {
        cryptographicKeyService.updateKeyUsages(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                UUID.fromString(uuid),
                request
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY, operation = Operation.UPDATE_KEY_USAGE)
    public void updateKeysUsages(BulkKeyUsageRequestDto request) {
        cryptographicKeyService.updateKeyUsages(
                request
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY_ITEM, operation = Operation.UPDATE_KEY_USAGE)
    public void updateKeyItemUsages(BulkKeyItemUsageRequestDto request) {
        cryptographicKeyService.updateKeyItemUsages(
                request
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.ATTRIBUTE, affiliatedResource = Resource.TOKEN, operation = Operation.LIST_ATTRIBUTES)
    public List<BaseAttribute> listCreateKeyAttributes(@LogResource(uuid = true, affiliated = true) String tokenInstanceUuid, String tokenProfileUuid, @LogResource(name = true) KeyRequestType type) throws ConnectorException {
        return cryptographicKeyService.listCreateKeyAttributes(
                UUID.fromString(tokenInstanceUuid),
                SecuredParentUUID.fromString(tokenProfileUuid),
                type
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY_ITEM, affiliatedResource = Resource.TOKEN, operation = Operation.HISTORY)
    public List<KeyEventHistoryDto> getEventHistory(@LogResource(uuid = true, affiliated = true) String tokenInstanceUuid, String uuid, @LogResource(uuid = true) String keyItemUuid) throws NotFoundException {
        return cryptographicKeyService.getEventHistory(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                UUID.fromString(uuid),
                UUID.fromString(keyItemUuid)
        );
    }
}
