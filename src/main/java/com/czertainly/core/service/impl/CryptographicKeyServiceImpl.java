package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.cryptography.key.KeyRequestDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.cryptography.key.KeyDetailDto;
import com.czertainly.api.model.core.cryptography.key.KeyDto;
import com.czertainly.core.service.CryptographicKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CryptographicKeyServiceImpl implements CryptographicKeyService {

    private static final Logger logger = LoggerFactory.getLogger(CryptographicKeyServiceImpl.class);

    @Override
    public List<KeyDto> listKeys(Optional<String> tokenInstanceUuid) {
        return null;
    }

    @Override
    public KeyDetailDto getKey(String tokenInstanceUuid, String uuid) throws NotFoundException {
        return null;
    }

    @Override
    public KeyDetailDto createKey(String tokenInstanceUuid, KeyRequestDto request) throws AlreadyExistException, ValidationException {
        return null;
    }

    @Override
    public void destroyKey(String tokenInstanceUuid, String uuid) throws NotFoundException {

    }

    @Override
    public List<BaseAttribute> listCreateKeyAttributes(String tokenInstanceUuid) throws NotFoundException {
        return null;
    }
}
