package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.CryptographicKeyController;
import com.czertainly.api.model.client.cryptography.key.KeyRequestDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.cryptography.key.KeyDetailDto;
import com.czertainly.api.model.core.cryptography.key.KeyDto;
import com.czertainly.core.service.CryptographicKeyService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

public class CryptographicKeyControllerImpl implements CryptographicKeyController {

    private CryptographicKeyService cryptographicKeyService;

    @Autowired
    public void setCryptographicKeyService(CryptographicKeyService cryptographicKeyService) {
        this.cryptographicKeyService = cryptographicKeyService;
    }

    @Override
    public List<KeyDto> listKeys(Optional<String> tokenInstanceUuid) {
        return cryptographicKeyService.listKeys(tokenInstanceUuid);
    }

    @Override
    public KeyDetailDto getKey(String tokenInstanceUuid, String uuid) throws NotFoundException {
        return cryptographicKeyService.getKey(tokenInstanceUuid, uuid);
    }

    @Override
    public KeyDetailDto createKey(String tokenInstanceUuid, KeyRequestDto request) throws AlreadyExistException, ValidationException {
        return cryptographicKeyService.createKey(tokenInstanceUuid, request);
    }

    @Override
    public void destroyKey(String tokenInstanceUuid, String uuid) throws NotFoundException {
        cryptographicKeyService.destroyKey(tokenInstanceUuid, uuid);
    }

    @Override
    public List<BaseAttribute> listCreateKeyAttributes(String tokenInstanceUuid) throws NotFoundException {
        return cryptographicKeyService.listCreateKeyAttributes(tokenInstanceUuid);
    }
}
