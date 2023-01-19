package com.czertainly.core.service.impl;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.cryptography.operations.CipherDataRequestDto;
import com.czertainly.api.model.client.cryptography.operations.RandomDataRequestDto;
import com.czertainly.api.model.client.cryptography.operations.SignDataRequestDto;
import com.czertainly.api.model.client.cryptography.operations.VerifyDataRequestDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.connector.cryptography.enums.CryptographicAlgorithm;
import com.czertainly.api.model.connector.cryptography.enums.KeyType;
import com.czertainly.api.model.connector.cryptography.operations.*;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cryptography.key.KeyEvent;
import com.czertainly.api.model.core.cryptography.key.KeyEventStatus;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.config.CryptographyProviderCsrSigner;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.CryptographicKeyItem;
import com.czertainly.core.dao.entity.TokenInstanceReference;
import com.czertainly.core.dao.repository.CryptographicKeyItemRepository;
import com.czertainly.core.dao.repository.CryptographicKeyRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.*;
import com.czertainly.core.util.CsrUtil;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CryptographicOperationServiceImpl implements CryptographicOperationService {

    private static final Logger logger = LoggerFactory.getLogger(CryptographicOperationServiceImpl.class);

    // --------------------------------------------------------------------------------
    // Services & API Clients
    // --------------------------------------------------------------------------------
    private MetadataService metadataService;
    private TokenInstanceService tokenInstanceService;
    private CryptographicKeyEventHistoryService eventHistoryService;
    private CryptographicOperationsApiClient cryptographicOperationsApiClient;
    private PermissionEvaluator permissionEvaluator;
    // --------------------------------------------------------------------------------
    // Repositories
    // --------------------------------------------------------------------------------
    private CryptographicKeyRepository cryptographicKeyRepository;
    private CryptographicKeyItemRepository cryptographicKeyItemRepository;

    // Setters
    @Autowired
    public void setMetadataService(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Autowired
    public void setTokenInstanceService(TokenInstanceService tokenInstanceService) {
        this.tokenInstanceService = tokenInstanceService;
    }

    @Autowired
    public void setEventHistoryService(CryptographicKeyEventHistoryService eventHistoryService) {
        this.eventHistoryService = eventHistoryService;
    }

    @Autowired
    public void setPermissionEvaluator(PermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    @Autowired
    public void setCryptographicOperationsApiClient(CryptographicOperationsApiClient cryptographicOperationsApiClient) {
        this.cryptographicOperationsApiClient = cryptographicOperationsApiClient;
    }

    @Autowired
    public void setCryptographicKeyRepository(CryptographicKeyRepository cryptographicKeyRepository) {
        this.cryptographicKeyRepository = cryptographicKeyRepository;
    }

    @Autowired
    public void setCryptographicKeyItemRepository(CryptographicKeyItemRepository cryptographicKeyItemRepository) {
        this.cryptographicKeyItemRepository = cryptographicKeyItemRepository;
    }

    // ----------------------------------------------------------------------------------------------
    // Service Implementations
    // ----------------------------------------------------------------------------------------------

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.ANY, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public List<BaseAttribute> listCipherAttributes(SecuredParentUUID tokenInstanceUuid, SecuredUUID tokenProfileUuid, UUID uuid, UUID keyItemUuid, CryptographicAlgorithm algorithm) throws ConnectorException {
        permissionEvaluator.tokenProfile(tokenProfileUuid);
        logger.info("Requesting to list cipher attributes for Key: {} and Algorithm {}", keyItemUuid, algorithm);
        CryptographicKeyItem key = getKeyItemEntity(keyItemUuid);
        logger.debug("Key details: {}", key);
        return cryptographicOperationsApiClient.listCipherAttributes(
                key.getCryptographicKey().getTokenProfile().getTokenInstanceReference().getConnector().mapToDto(),
                key.getCryptographicKey().getTokenProfile().getTokenInstanceReferenceUuid().toString(),
                key.getKeyReferenceUuid().toString());
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.ENCRYPT)
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.ENCRYPT, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public EncryptDataResponseDto encryptData(SecuredParentUUID tokenInstanceUuid, SecuredUUID tokenProfileUuid, UUID uuid, UUID keyItemUuid, CipherDataRequestDto request) throws ConnectorException {
        permissionEvaluator.tokenProfile(tokenProfileUuid);
        logger.info("Request to encrypt the data using the key: {} and data: {}", keyItemUuid, request);
        CryptographicKeyItem key = getKeyItemEntity(keyItemUuid);
        logger.debug("Key details: {}", key);
        if (request.getCipherData() == null) {
            throw new ValidationException(ValidationError.create("Cannot encrypt null data"));
        }
        com.czertainly.api.model.connector.cryptography.operations.CipherDataRequestDto requestDto = new com.czertainly.api.model.connector.cryptography.operations.CipherDataRequestDto();
        requestDto.setCipherData(request.getCipherData());
        requestDto.setCipherAttributes(request.getCipherAttributes());
        logger.debug("Request to the connector: {}", requestDto);
        try {
            EncryptDataResponseDto response = cryptographicOperationsApiClient.encryptData(
                    key.getCryptographicKey().getTokenProfile().getTokenInstanceReference().getConnector().mapToDto(),
                    key.getCryptographicKey().getTokenProfile().getTokenInstanceReferenceUuid().toString(),
                    key.getKeyReferenceUuid().toString(),
                    requestDto
            );
            eventHistoryService.addEventHistory(KeyEvent.ENCRYPT, KeyEventStatus.SUCCESS,
                    "Encryption of data success ", null, key);
            return response;
        } catch (Exception e) {
            eventHistoryService.addEventHistory(KeyEvent.ENCRYPT, KeyEventStatus.FAILED,
                    "Encryption of data failed ", Collections.singletonMap("exception", e.getLocalizedMessage()), key);
            throw e;
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.DECRYPT)
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.DECRYPT, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public DecryptDataResponseDto decryptData(SecuredParentUUID tokenInstanceUuid, SecuredUUID tokenProfileUuid, UUID uuid, UUID keyItemUuid, CipherDataRequestDto request) throws ConnectorException {
        permissionEvaluator.tokenProfile(tokenProfileUuid);
        logger.info("Decrypting using the key: {} and data: {}", keyItemUuid, request);
        CryptographicKeyItem key = getKeyItemEntity(keyItemUuid);
        logger.debug("Key details: {}", key);
        if (request.getCipherData() == null) {
            throw new ValidationException(ValidationError.create("Cannot decrypt null data"));
        }
        com.czertainly.api.model.connector.cryptography.operations.CipherDataRequestDto requestDto = new com.czertainly.api.model.connector.cryptography.operations.CipherDataRequestDto();
        requestDto.setCipherData(request.getCipherData());
        requestDto.setCipherAttributes(request.getCipherAttributes());
        logger.debug("Request to the connector: {}", requestDto);
        try {
            DecryptDataResponseDto response = cryptographicOperationsApiClient.decryptData(
                    key.getCryptographicKey().getTokenProfile().getTokenInstanceReference().getConnector().mapToDto(),
                    key.getCryptographicKey().getTokenProfile().getTokenInstanceReferenceUuid().toString(),
                    key.getKeyReferenceUuid().toString(),
                    requestDto);
            eventHistoryService.addEventHistory(KeyEvent.ENCRYPT, KeyEventStatus.SUCCESS,
                    "Decryption of data success ", null, key);
            return response;
        } catch (Exception e) {
            eventHistoryService.addEventHistory(KeyEvent.DECRYPT, KeyEventStatus.FAILED,
                    "Encryption of data failed ", Collections.singletonMap("exception", e.getLocalizedMessage()), key);
            throw e;
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.ANY, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public List<BaseAttribute> listSignatureAttributes(SecuredParentUUID tokenInstanceUuid, SecuredUUID tokenProfileUuid, UUID uuid, UUID keyItemUuid, CryptographicAlgorithm algorithm) throws ConnectorException {
        permissionEvaluator.tokenProfile(tokenProfileUuid);
        logger.info("Requesting to list the Signature Attributes for key: {} and Algorithm: {}", keyItemUuid, algorithm);
        CryptographicKeyItem key = getKeyItemEntity(keyItemUuid);
        logger.debug("Key details: {}", key);
        return cryptographicOperationsApiClient.listSignatureAttributes(
                key.getCryptographicKey().getTokenProfile().getTokenInstanceReference().getConnector().mapToDto(),
                key.getCryptographicKey().getTokenProfile().getTokenInstanceReferenceUuid().toString(),
                key.getKeyReferenceUuid().toString()
        );
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.SIGN)
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.SIGN, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public SignDataResponseDto signData(SecuredParentUUID tokenInstanceUuid, SecuredUUID tokenProfileUuid, UUID uuid, UUID keyItemUuid, SignDataRequestDto request) throws ConnectorException {
        permissionEvaluator.tokenProfile(tokenProfileUuid);
        logger.info("Signing the data: {} using the key: {}", request, keyItemUuid);
        CryptographicKeyItem key = getKeyItemEntity(keyItemUuid);
        logger.debug("Key details: {}", key);
        if (request.getData() == null) {
            throw new ValidationException(ValidationError.create("Cannot sign empty data"));
        }
        com.czertainly.api.model.connector.cryptography.operations.SignDataRequestDto requestDto = new com.czertainly.api.model.connector.cryptography.operations.SignDataRequestDto();
        requestDto.setSignatureAttributes(request.getSignatureAttributes());
        requestDto.setData(request.getData());
        logger.debug("Request to the connector: {}", requestDto);
        try {
            SignDataResponseDto response = cryptographicOperationsApiClient.signData(
                    key.getCryptographicKey().getTokenProfile().getTokenInstanceReference().getConnector().mapToDto(),
                    key.getCryptographicKey().getTokenProfile().getTokenInstanceReferenceUuid().toString(),
                    key.getKeyReferenceUuid().toString(),
                    requestDto
            );
            eventHistoryService.addEventHistory(KeyEvent.ENCRYPT, KeyEventStatus.SUCCESS,
                    "Signing data success ", null, key);
            return response;
        } catch (Exception e) {
            eventHistoryService.addEventHistory(KeyEvent.SIGN, KeyEventStatus.FAILED,
                    "Encryption of data failed ", Collections.singletonMap("exception", e.getLocalizedMessage()), key);
            throw e;
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.VERIFY)
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.VERIFY, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public VerifyDataResponseDto verifyData(SecuredParentUUID tokenInstanceUuid, SecuredUUID tokenProfileUuid, UUID uuid, UUID keyItemUuid, VerifyDataRequestDto request) throws ConnectorException {
        permissionEvaluator.tokenProfile(tokenProfileUuid);
        logger.info("Request to verify data: {} for the key: {}", request, keyItemUuid);
        CryptographicKeyItem key = getKeyItemEntity(keyItemUuid);
        logger.debug("Key details: {}", key);
        if (request.getSignatures() == null) {
            throw new ValidationException(ValidationError.create("Cannot verify empty data"));
        }
        com.czertainly.api.model.connector.cryptography.operations.VerifyDataRequestDto requestDto = new com.czertainly.api.model.connector.cryptography.operations.VerifyDataRequestDto();
        requestDto.setSignatureAttributes(request.getSignatureAttributes());
        requestDto.setData(request.getData());
        requestDto.setSignatures(request.getSignatures());
        logger.debug("Request to the connector: {}", requestDto);
        try {
            VerifyDataResponseDto response = cryptographicOperationsApiClient.verifyData(
                    key.getCryptographicKey().getTokenProfile().getTokenInstanceReference().getConnector().mapToDto(),
                    key.getCryptographicKey().getTokenProfile().getTokenInstanceReferenceUuid().toString(),
                    key.getKeyReferenceUuid().toString(),
                    requestDto
            );
            eventHistoryService.addEventHistory(KeyEvent.ENCRYPT, KeyEventStatus.SUCCESS,
                    "Verification of data completed ", null, key);
            return response;
        } catch (Exception e) {
            eventHistoryService.addEventHistory(KeyEvent.ENCRYPT, KeyEventStatus.FAILED,
                    "Encryption of data failed ", Collections.singletonMap("exception", e.getLocalizedMessage()), key);
            throw e;
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.TOKEN, action = ResourceAction.ANY)
    public List<BaseAttribute> listRandomAttributes(SecuredUUID tokenInstanceUuid) throws ConnectorException {
        logger.info("Requesting attributes for random generation for token Instance: {}", tokenInstanceUuid);
        TokenInstanceReference tokenInstanceReference = tokenInstanceService.getTokenInstanceEntity(tokenInstanceUuid);
        logger.debug("Token Instance details: {}", tokenInstanceReference);
        return cryptographicOperationsApiClient.listRandomAttributes(
                tokenInstanceReference.getConnector().mapToDto(),
                tokenInstanceReference.getTokenInstanceUuid()
        );
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.TOKEN, action = ResourceAction.DETAIL)
    public RandomDataResponseDto randomData(SecuredUUID tokenInstanceUuid, RandomDataRequestDto request) throws ConnectorException {
        logger.info("Requesting attributes for random generation for token Instance: {}", tokenInstanceUuid);
        TokenInstanceReference tokenInstanceReference = tokenInstanceService.getTokenInstanceEntity(tokenInstanceUuid);
        logger.debug("Token Instance details: {}", tokenInstanceReference);
        com.czertainly.api.model.connector.cryptography.operations.RandomDataRequestDto requestDto = new com.czertainly.api.model.connector.cryptography.operations.RandomDataRequestDto();
        requestDto.setAttributes(request.getAttributes());
        requestDto.setLength(request.getLength());
        logger.debug("Request to the connector: {}", requestDto);
        return cryptographicOperationsApiClient.randomData(
                tokenInstanceReference.getConnector().mapToDto(),
                tokenInstanceReference.getTokenInstanceUuid(),
                requestDto
        );
    }

    @Override
    public String generateCsr(UUID keyUuid, UUID tokenProfileUuid, List<RequestAttributeDto> csrAttributes, List<RequestAttributeDto> signatureAttributes) throws NotFoundException, NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        // Check if the UUID of the Key is empty
        if (keyUuid == null) {
            throw new ValidationException(
                    ValidationError.create(
                            "Key UUID Cannot be empty"
                    )
            );
        }

        // Token Profile UUID of the request cannot be empty
        if (tokenProfileUuid == null) {
            throw new ValidationException(
                    ValidationError.create(
                            "Token Profile UUID Cannot be empty"
                    )
            );
        }

        // Check the Permission of the token profile to the user
        permissionEvaluator.tokenProfile(SecuredUUID.fromUUID(tokenProfileUuid));
        CryptographicKey key = cryptographicKeyRepository.findByUuid(
                keyUuid).orElseThrow(
                        () -> new NotFoundException(
                                CryptographicKey.class,
                                keyUuid
                        )
        );
        CryptographicKeyItem privateKeyItem = null;
        CryptographicKeyItem publicKeyItem = null;

        // Iterate through the items inside the key and assign the private and public Key
        for (CryptographicKeyItem item : key.getItems()) {
            if (item.getType().equals(KeyType.PRIVATE_KEY)) {
                privateKeyItem = item;
            } else if (item.getType().equals(KeyType.PUBLIC_KEY)) {
                publicKeyItem = item;
            } else {
                //do nothing
            }
        }
        List.of(privateKeyItem, publicKeyItem).forEach(e -> {
            if (e == null) {
                throw new ValidationException(
                        ValidationError.create(
                                "Selected item does not contain the complete keypair"
                        )
                );
            }
        });

        // Generate the CSR
        return generateCsr(
                csrAttributes,
                publicKeyItem.getKeyData(),
                privateKeyItem,
                signatureAttributes
        );
    }

    private CryptographicKeyItem getKeyItemEntity(UUID uuid) throws NotFoundException {
        logger.debug("UUID of the key to get the entity: {}", uuid);
        CryptographicKeyItem key = cryptographicKeyItemRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(CryptographicKeyItem.class, uuid));
        if (key.getCryptographicKey().getTokenProfile().getTokenInstanceReference() == null) {
            throw new NotFoundException("Token Instance associated with the Key is not found");
        }
        if (key.getCryptographicKey().getTokenProfile().getTokenInstanceReference().getConnector() == null) {
            throw new NotFoundException(("Connector associated to the Key is not found"));
        }
        logger.debug("Key Instance: {}", key);
        return key;
    }

    public String generateCsr(List<RequestAttributeDto> attributes, String key, CryptographicKeyItem keyItem, List<RequestAttributeDto> signatureAttributes) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        // Add bouncy castle provider
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        // Build bouny castle p10 builder
        PKCS10CertificationRequestBuilder p10Builder = new JcaPKCS10CertificationRequestBuilder(
                CsrUtil.buildSubject(attributes),
                CsrUtil.publicKeyObjectFromString(key)
        );

        // Assign the custom signer to sign the CSR with the private key from the cryptography provider
        ContentSigner signer = new CryptographyProviderCsrSigner(
                cryptographicOperationsApiClient,
                keyItem.getCryptographicKey().getTokenInstanceReference().getConnector().mapToDto(),
                keyItem.getCryptographicKey().getTokenInstanceReferenceUuid(),
                keyItem.getKeyReferenceUuid(),
                signatureAttributes
        );

        // Build the CSR with the DN generated and the signer
        PKCS10CertificationRequest csr = p10Builder.build(signer);

        // Convert the data from byte array to string
        return CsrUtil.byteArrayCsrToString(csr.getEncoded());
    }
}
