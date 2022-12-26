package com.czertainly.core.api.web;

import com.czertainly.api.exception.CryptographicOperationException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.CryptographicOperationsController;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.connector.cryptography.enums.CryptographicAlgorithm;
import com.czertainly.api.model.connector.cryptography.operations.*;
import com.czertainly.core.service.CryptographicOperationService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class CryptographicOperationControllerImpl implements CryptographicOperationsController {

    private CryptographicOperationService cryptographicOperationService;

    @Autowired
    public void setCryptographicOperationService(CryptographicOperationService cryptographicOperationService) {
        this.cryptographicOperationService = cryptographicOperationService;
    }

    @Override
    public List<BaseAttribute> listCipherAttributes(String tokenInstanceUuid, CryptographicAlgorithm algorithm) throws NotFoundException {
        return cryptographicOperationService.listCipherAttributes(tokenInstanceUuid, algorithm);
    }

    @Override
    public EncryptDataResponseDto encryptData(String tokenInstanceUuid, CipherDataRequestDto request) throws NotFoundException, CryptographicOperationException {
        return cryptographicOperationService.encryptData(tokenInstanceUuid, request);
    }

    @Override
    public DecryptDataResponseDto decryptData(String tokenInstanceUuid, CipherDataRequestDto request) throws NotFoundException, CryptographicOperationException {
        return cryptographicOperationService.decryptData(tokenInstanceUuid, request);
    }

    @Override
    public List<BaseAttribute> listSignatureAttributes(String tokenInstanceUuid, CryptographicAlgorithm algorithm) throws NotFoundException {
        return cryptographicOperationService.listSignatureAttributes(tokenInstanceUuid, algorithm);
    }

    @Override
    public SignDataResponseDto signData(String tokenInstanceUuid, SignDataRequestDto request) throws NotFoundException, CryptographicOperationException {
        return cryptographicOperationService.signData(tokenInstanceUuid, request);
    }

    @Override
    public VerifyDataResponseDto verifyData(String tokenInstanceUuid, VerifyDataRequestDto request) throws NotFoundException, CryptographicOperationException {
        return cryptographicOperationService.verifyData(tokenInstanceUuid, request);
    }

    @Override
    public List<BaseAttribute> listRandomAttributes(String tokenInstanceUuid) throws NotFoundException {
        return cryptographicOperationService.listRandomAttributes(tokenInstanceUuid);
    }

    @Override
    public RandomDataResponseDto randomData(String tokenInstanceUuid, RandomDataRequestDto request) throws NotFoundException, CryptographicOperationException {
        return cryptographicOperationService.randomData(tokenInstanceUuid, request);
    }
}
