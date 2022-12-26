package com.czertainly.core.service.impl;

import com.czertainly.api.exception.CryptographicOperationException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.connector.cryptography.enums.CryptographicAlgorithm;
import com.czertainly.api.model.connector.cryptography.operations.*;
import com.czertainly.core.service.CryptographicOperationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CryptographicOperationServiceImpl implements CryptographicOperationService {

    private static final Logger logger = LoggerFactory.getLogger(CryptographicOperationServiceImpl.class);

    @Override
    public List<BaseAttribute> listCipherAttributes(String tokenInstanceUuid, CryptographicAlgorithm algorithm) throws NotFoundException {
        return null;
    }

    @Override
    public EncryptDataResponseDto encryptData(String tokenInstanceUuid, CipherDataRequestDto request) throws NotFoundException, CryptographicOperationException {
        return null;
    }

    @Override
    public DecryptDataResponseDto decryptData(String tokenInstanceUuid, CipherDataRequestDto request) throws NotFoundException, CryptographicOperationException {
        return null;
    }

    @Override
    public List<BaseAttribute> listSignatureAttributes(String tokenInstanceUuid, CryptographicAlgorithm algorithm) throws NotFoundException {
        return null;
    }

    @Override
    public SignDataResponseDto signData(String tokenInstanceUuid, SignDataRequestDto request) throws NotFoundException, CryptographicOperationException {
        return null;
    }

    @Override
    public VerifyDataResponseDto verifyData(String tokenInstanceUuid, VerifyDataRequestDto request) throws NotFoundException, CryptographicOperationException {
        return null;
    }

    @Override
    public List<BaseAttribute> listRandomAttributes(String tokenInstanceUuid) throws NotFoundException {
        return null;
    }

    @Override
    public RandomDataResponseDto randomData(String tokenInstanceUuid, RandomDataRequestDto request) throws NotFoundException, CryptographicOperationException {
        return null;
    }
}
