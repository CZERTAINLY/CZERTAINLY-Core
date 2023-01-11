package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.CryptographicKeyController;
import com.czertainly.api.model.client.cryptography.key.BulkKeyUsageRequestDto;
import com.czertainly.api.model.client.cryptography.key.EditKeyRequestDto;
import com.czertainly.api.model.client.cryptography.key.KeyRequestDto;
import com.czertainly.api.model.client.cryptography.key.KeyRequestType;
import com.czertainly.api.model.client.cryptography.key.UpdateKeyUsageRequestDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.cryptography.key.KeyDetailDto;
import com.czertainly.api.model.core.cryptography.key.KeyDto;
import com.czertainly.api.model.core.cryptography.key.KeyEventHistoryDto;
import com.czertainly.core.security.authz.SecuredParentUUID;
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
    public List<KeyDto> listKeys(Optional<String> tokenProfileUuid) {
        return cryptographicKeyService.listKeys(tokenProfileUuid, SecurityFilter.create());
    }

    @Override
    public KeyDetailDto getKey(String tokenInstanceUuid, String uuid) throws NotFoundException {
        return cryptographicKeyService.getKey(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                uuid
        );
    }

    @Override
    public KeyDetailDto createKey(String tokenInstanceUuid, String tokenProfileUuid, KeyRequestType type, KeyRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException {
        return cryptographicKeyService.createKey(
                UUID.fromString(tokenInstanceUuid),
                SecuredParentUUID.fromString(tokenProfileUuid),
                type,
                request
        );
    }

    @Override
    public KeyDetailDto editKey(String tokenInstanceUuid, String uuid, EditKeyRequestDto request) throws ConnectorException {
        return cryptographicKeyService.editKey(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                UUID.fromString(uuid),
                request
        );
    }

    @Override
    public void compromiseKey(String tokenInstanceUuid, String uuid, List<String> uuids) throws NotFoundException {
        cryptographicKeyService.compromiseKey(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                UUID.fromString(uuid),
                uuids
        );
    }

    @Override
    public void compromiseKeys(List<String> uuids) {
        cryptographicKeyService.compromiseKey(
                uuids
        );
    }

    @Override
    public void destroyKey(String tokenInstanceUuid, String uuid, List<String> keyUuids) throws ConnectorException {
        cryptographicKeyService.destroyKey(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                uuid,
                keyUuids
        );
    }

    @Override
    public void destroyKey(List<String> keyUuids) throws ConnectorException {
        cryptographicKeyService.destroyKey(
                keyUuids
        );
    }

    @Override
    public void deleteKey(String tokenInstanceUuid, String uuid, List<String> keyUuids) throws ConnectorException {
        cryptographicKeyService.deleteKey(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                UUID.fromString(uuid),
                keyUuids
        );
    }

    @Override
    public void deleteKeys(List<String> keyUuids) throws ConnectorException {
        cryptographicKeyService.deleteKey(
                keyUuids
        );
    }

    @Override
    public void enableKey(String tokenInstanceUuid, String uuid, List<String> keyUuids) throws NotFoundException {
        cryptographicKeyService.enableKey(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                UUID.fromString(uuid),
                keyUuids
        );
    }

    @Override
    public void bulkEnableKeys(List<String> uuids) {
        cryptographicKeyService.enableKey(
                uuids
        );
    }

    @Override
    public void disableKey(String tokenInstanceUuid, String uuid, List<String> keyUuids) throws NotFoundException {
        cryptographicKeyService.disableKey(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                UUID.fromString(uuid),
                keyUuids
        );
    }

    @Override
    public void bulkDisableKeys(List<String> uuids) {
        cryptographicKeyService.disableKey(
                uuids
        );
    }

    @Override
    public void updateKeyUsages(String tokenInstanceUuid, String uuid, UpdateKeyUsageRequestDto request) throws NotFoundException, ValidationException {
        cryptographicKeyService.updateKeyUsages(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                UUID.fromString(uuid),
                request
        );
    }

    @Override
    public void updateKeysUsages(BulkKeyUsageRequestDto request) {
        cryptographicKeyService.updateKeyUsages(
                request
        );
    }

    @Override
    public List<BaseAttribute> listCreateKeyAttributes(String tokenInstanceUuid, String tokenProfileUuid, KeyRequestType type) throws ConnectorException {
        return cryptographicKeyService.listCreateKeyAttributes(
                UUID.fromString(tokenInstanceUuid),
                SecuredParentUUID.fromString(tokenProfileUuid),
                type
        );
    }

    @Override
    public List<KeyEventHistoryDto> getEventHistory(String tokenInstanceUuid, String uuid, String keyItemUuid) throws NotFoundException {
        return cryptographicKeyService.getEventHistory(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                UUID.fromString(uuid),
                UUID.fromString(keyItemUuid)
        );
    }
}
