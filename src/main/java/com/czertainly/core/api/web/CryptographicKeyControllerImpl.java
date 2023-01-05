package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.CryptographicKeyController;
import com.czertainly.api.model.client.cryptography.key.EditKeyRequestDto;
import com.czertainly.api.model.client.cryptography.key.KeyRequestDto;
import com.czertainly.api.model.client.cryptography.key.KeyRequestType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.cryptography.key.KeyDetailDto;
import com.czertainly.api.model.core.cryptography.key.KeyDto;
import com.czertainly.api.model.core.cryptography.tokenprofile.TokenProfileDetailDto;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.CryptographicKeyService;
import org.springframework.beans.factory.annotation.Autowired;
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
    public KeyDetailDto createKey(
            String tokenInstanceUuid,
            KeyRequestType type,
            KeyRequestDto request
    ) throws AlreadyExistException, ValidationException, ConnectorException {
        return cryptographicKeyService.createKey(
                SecuredParentUUID.fromString(tokenInstanceUuid),
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
    public void compromiseKey(String tokenInstanceUuid, String uuid, List<String> keyUuids) throws NotFoundException {
        cryptographicKeyService.compromiseKey(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                UUID.fromString(uuid),
                keyUuids
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
    public void deleteKey(String tokenInstanceUuid, String uuid, List<String> keyUuids) throws ConnectorException {
        cryptographicKeyService.deleteKey(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                UUID.fromString(uuid),
                keyUuids
        );
    }

    @Override
    public void disableKey(String tokenInstanceUuid, String uuid) throws NotFoundException {
        cryptographicKeyService.disableKey(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                UUID.fromString(uuid)
        );
    }

    @Override
    public void enableTokenProfile(String tokenInstanceUuid, String uuid) throws NotFoundException {
        cryptographicKeyService.enableKey(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                UUID.fromString(uuid)
        );
    }

    @Override
    public void bulkDisableRaProfile(List<String> uuids) {
        cryptographicKeyService.disableKey(
                SecuredUUID.fromList(uuids)
        );
    }

    @Override
    public void bulkEnableRaProfile(List<String> uuids) {
        cryptographicKeyService.enableKey(
                SecuredUUID.fromList(uuids)
        );
    }

    @Override
    public List<BaseAttribute> listCreateKeyAttributes(String tokenInstanceUuid, KeyRequestType type) throws ConnectorException {
        return cryptographicKeyService.listCreateKeyAttributes(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                type
        );
    }
}
