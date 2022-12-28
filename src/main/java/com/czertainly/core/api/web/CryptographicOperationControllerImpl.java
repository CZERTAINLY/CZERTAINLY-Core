package com.czertainly.core.api.web;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.CryptographicOperationException;
import com.czertainly.api.interfaces.core.web.CryptographicOperationsController;
import com.czertainly.api.model.client.cryptography.operations.CipherDataRequestDto;
import com.czertainly.api.model.client.cryptography.operations.RandomDataRequestDto;
import com.czertainly.api.model.client.cryptography.operations.SignDataRequestDto;
import com.czertainly.api.model.client.cryptography.operations.VerifyDataRequestDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.connector.cryptography.enums.CryptographicAlgorithm;
import com.czertainly.api.model.connector.cryptography.operations.DecryptDataResponseDto;
import com.czertainly.api.model.connector.cryptography.operations.EncryptDataResponseDto;
import com.czertainly.api.model.connector.cryptography.operations.RandomDataResponseDto;
import com.czertainly.api.model.connector.cryptography.operations.SignDataResponseDto;
import com.czertainly.api.model.connector.cryptography.operations.VerifyDataResponseDto;
import com.czertainly.core.service.CryptographicOperationService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

public class CryptographicOperationControllerImpl implements CryptographicOperationsController {

    private CryptographicOperationService cryptographicOperationService;

    @Autowired
    public void setCryptographicOperationService(CryptographicOperationService cryptographicOperationService) {
        this.cryptographicOperationService = cryptographicOperationService;
    }

    @Override
    public List<BaseAttribute> listCipherAttributes(String uuid, CryptographicAlgorithm algorithm) throws ConnectorException {
        return cryptographicOperationService.listCipherAttributes(UUID.fromString(uuid), algorithm);
    }

    @Override
    public EncryptDataResponseDto encryptData(String uuid, CipherDataRequestDto request) throws ConnectorException, CryptographicOperationException {
        return cryptographicOperationService.encryptData(UUID.fromString(uuid), request);
    }

    @Override
    public DecryptDataResponseDto decryptData(String uuid, CipherDataRequestDto request) throws ConnectorException, CryptographicOperationException {
        return cryptographicOperationService.decryptData(UUID.fromString(uuid), request);
    }

    @Override
    public List<BaseAttribute> listSignatureAttributes(String uuid, CryptographicAlgorithm algorithm) throws ConnectorException {
        return cryptographicOperationService.listSignatureAttributes(UUID.fromString(uuid), algorithm);
    }

    @Override
    public SignDataResponseDto signData(String uuid, SignDataRequestDto request) throws ConnectorException, CryptographicOperationException {
        return cryptographicOperationService.signData(UUID.fromString(uuid), request);
    }

    @Override
    public VerifyDataResponseDto verifyData(String uuid, VerifyDataRequestDto request) throws ConnectorException, CryptographicOperationException {
        return cryptographicOperationService.verifyData(UUID.fromString(uuid), request);
    }

    @Override
    public List<BaseAttribute> listRandomAttributes(String uuid) throws ConnectorException {
        return cryptographicOperationService.listRandomAttributes(UUID.fromString(uuid));
    }

    @Override
    public RandomDataResponseDto randomData(String uuid, RandomDataRequestDto request) throws ConnectorException, CryptographicOperationException {
        return cryptographicOperationService.randomData(UUID.fromString(uuid), request);
    }
}
