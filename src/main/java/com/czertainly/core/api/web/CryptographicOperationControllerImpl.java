package com.czertainly.core.api.web;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.interfaces.core.web.CryptographicOperationsController;
import com.czertainly.api.model.client.cryptography.operations.*;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CryptographicOperationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class CryptographicOperationControllerImpl implements CryptographicOperationsController {

    private CryptographicOperationService cryptographicOperationService;

    @Autowired
    public void setCryptographicOperationService(CryptographicOperationService cryptographicOperationService) {
        this.cryptographicOperationService = cryptographicOperationService;
    }

    @Override
    public List<BaseAttribute> listCipherAttributes(
            String tokenInstanceUuid,
            String tokenProfileUuid,
            String uuid,
            String keyItemUuid,
            KeyAlgorithm keyAlgorithm
    ) throws ConnectorException {
        return cryptographicOperationService.listCipherAttributes(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                SecuredUUID.fromString(tokenProfileUuid),
                UUID.fromString(uuid),
                UUID.fromString(keyItemUuid),
                keyAlgorithm
        );
    }

    @Override
    public EncryptDataResponseDto encryptData(
            String tokenInstanceUuid,
            String tokenProfileUuid,
            String uuid,
            String keyItemUuid,
            CipherDataRequestDto request
    ) throws ConnectorException {
        return cryptographicOperationService.encryptData(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                SecuredUUID.fromString(tokenProfileUuid),
                UUID.fromString(uuid),
                UUID.fromString(keyItemUuid),
                request);
    }

    @Override
    public DecryptDataResponseDto decryptData(
            String tokenInstanceUuid,
            String tokenProfileUuid,
            String uuid,
            String keyItemUuid,
            CipherDataRequestDto request)
            throws ConnectorException {
        return cryptographicOperationService.decryptData(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                SecuredUUID.fromString(tokenProfileUuid),
                UUID.fromString(uuid),
                UUID.fromString(keyItemUuid),
                request);
    }

    @Override
    public List<BaseAttribute> listSignatureAttributes(
            String tokenInstanceUuid,
            String tokenProfileUuid,
            String uuid,
            String keyItemUuid,
            KeyAlgorithm keyAlgorithm
    ) throws ConnectorException {
        return cryptographicOperationService.listSignatureAttributes(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                SecuredUUID.fromString(tokenProfileUuid),
                UUID.fromString(uuid),
                UUID.fromString(keyItemUuid),
                keyAlgorithm
        );
    }

    @Override
    public SignDataResponseDto signData(
            String tokenInstanceUuid,
            String tokenProfileUuid,
            String uuid,
            String keyItemUuid,
            SignDataRequestDto request
    ) throws ConnectorException {
        return cryptographicOperationService.signData(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                SecuredUUID.fromString(tokenProfileUuid),
                UUID.fromString(uuid),
                UUID.fromString(keyItemUuid),
                request
        );
    }

    @Override
    public VerifyDataResponseDto verifyData(
            String tokenInstanceUuid,
            String tokenProfileUuid,
            String uuid,
            String keyItemUuid,
            VerifyDataRequestDto request
    ) throws ConnectorException {
        return cryptographicOperationService.verifyData(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                SecuredUUID.fromString(tokenProfileUuid),
                UUID.fromString(uuid),
                UUID.fromString(keyItemUuid),
                request
        );
    }

    @Override
    public List<BaseAttribute> listRandomAttributes(
            String tokenInstanceUuid
    ) throws ConnectorException {
        return cryptographicOperationService.listRandomAttributes(
                SecuredUUID.fromString(tokenInstanceUuid)
        );
    }

    @Override
    public RandomDataResponseDto randomData(
            String tokenInstanceUuid,
            RandomDataRequestDto request
    ) throws ConnectorException {
        return cryptographicOperationService.randomData(
                SecuredUUID.fromString(tokenInstanceUuid),
                request
        );
    }
}
