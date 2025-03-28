package com.czertainly.core.api.web;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.CryptographicOperationsController;
import com.czertainly.api.model.client.cryptography.operations.*;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.logging.LogResource;
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
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY_ITEM, affiliatedResource = Resource.TOKEN, operation = Operation.ENCRYPT)
    public EncryptDataResponseDto encryptData(
            @LogResource(uuid = true, affiliated = true) String tokenInstanceUuid,
            String tokenProfileUuid,
            String uuid,
            @LogResource(uuid = true) String keyItemUuid,
            CipherDataRequestDto request
    ) throws ConnectorException, NotFoundException {
        return cryptographicOperationService.encryptData(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                SecuredUUID.fromString(tokenProfileUuid),
                UUID.fromString(uuid),
                UUID.fromString(keyItemUuid),
                request);
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY_ITEM, affiliatedResource = Resource.TOKEN, operation = Operation.DECRYPT)
    public DecryptDataResponseDto decryptData(
            @LogResource(uuid = true, affiliated = true) String tokenInstanceUuid,
            String tokenProfileUuid,
            String uuid,
            @LogResource(uuid = true) String keyItemUuid,
            CipherDataRequestDto request)
            throws ConnectorException, NotFoundException {
        return cryptographicOperationService.decryptData(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                SecuredUUID.fromString(tokenProfileUuid),
                UUID.fromString(uuid),
                UUID.fromString(keyItemUuid),
                request);
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY, affiliatedResource = Resource.TOKEN, operation = Operation.SIGN)
    public SignDataResponseDto signData(
            @LogResource(uuid = true, affiliated = true) String tokenInstanceUuid,
            String tokenProfileUuid,
            String uuid,
            @LogResource(uuid = true) String keyItemUuid,
            SignDataRequestDto request
    ) throws ConnectorException, NotFoundException {
        return cryptographicOperationService.signData(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                SecuredUUID.fromString(tokenProfileUuid),
                UUID.fromString(uuid),
                UUID.fromString(keyItemUuid),
                request
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.CRYPTOGRAPHIC_KEY, affiliatedResource = Resource.TOKEN, operation = Operation.VERIFY)
    public VerifyDataResponseDto verifyData(
            @LogResource(uuid = true, affiliated = true) String tokenInstanceUuid,
            String tokenProfileUuid,
            String uuid,
            @LogResource(uuid = true) String keyItemUuid,
            VerifyDataRequestDto request
    ) throws ConnectorException, NotFoundException {
        return cryptographicOperationService.verifyData(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                SecuredUUID.fromString(tokenProfileUuid),
                UUID.fromString(uuid),
                UUID.fromString(keyItemUuid),
                request
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.TOKEN, operation = Operation.RANDOM_DATA)
    public RandomDataResponseDto randomData(
            @LogResource(uuid = true) String tokenInstanceUuid,
            RandomDataRequestDto request
    ) throws ConnectorException, NotFoundException {
        return cryptographicOperationService.randomData(
                SecuredUUID.fromString(tokenInstanceUuid),
                request
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.ATTRIBUTE, name = "signature", affiliatedResource = Resource.CRYPTOGRAPHIC_KEY_ITEM, operation = Operation.LIST_ATTRIBUTES)
    public List<BaseAttribute> listSignatureAttributes(
            String tokenInstanceUuid,
            String tokenProfileUuid,
            String uuid,
            @LogResource(uuid = true, affiliated = true) String keyItemUuid,
            KeyAlgorithm algorithm
    ) throws ConnectorException, NotFoundException {
        return cryptographicOperationService.listSignatureAttributes(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                SecuredUUID.fromString(tokenProfileUuid),
                UUID.fromString(uuid),
                UUID.fromString(keyItemUuid),
                algorithm
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.ATTRIBUTE, name = "cipher", affiliatedResource = Resource.CRYPTOGRAPHIC_KEY_ITEM, operation = Operation.LIST_ATTRIBUTES)
    public List<BaseAttribute> listCipherAttributes(
            String tokenInstanceUuid,
            String tokenProfileUuid,
            String uuid,
            @LogResource(uuid = true, affiliated = true) String keyItemUuid,
            KeyAlgorithm algorithm
    ) throws ConnectorException, NotFoundException {
        return cryptographicOperationService.listCipherAttributes(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                SecuredUUID.fromString(tokenProfileUuid),
                UUID.fromString(uuid),
                UUID.fromString(keyItemUuid),
                algorithm
        );
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.ATTRIBUTE, name = "random", affiliatedResource = Resource.TOKEN, operation = Operation.LIST_ATTRIBUTES)
    public List<BaseAttribute> listRandomAttributes(
            @LogResource(uuid = true, affiliated = true) String tokenInstanceUuid
    ) throws ConnectorException, NotFoundException {
        return cryptographicOperationService.listRandomAttributes(
                SecuredUUID.fromString(tokenInstanceUuid)
        );
    }
}
