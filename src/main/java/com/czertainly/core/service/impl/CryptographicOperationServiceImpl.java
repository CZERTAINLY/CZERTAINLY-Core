package com.czertainly.core.service.impl;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.cryptography.operations.*;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.connector.cryptography.operations.data.CipherRequestData;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cryptography.key.KeyEvent;
import com.czertainly.api.model.core.cryptography.key.KeyEventStatus;
import com.czertainly.api.model.core.cryptography.key.KeyState;
import com.czertainly.api.model.core.cryptography.key.KeyUsage;
import com.czertainly.core.attribute.EcdsaSignatureAttributes;
import com.czertainly.core.attribute.RsaEncryptionAttributes;
import com.czertainly.core.attribute.RsaSignatureAttributes;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.attribute.engine.AttributeOperation;
import com.czertainly.core.config.TokenContentSigner;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.CryptographicKeyItem;
import com.czertainly.core.dao.entity.TokenInstanceReference;
import com.czertainly.core.dao.repository.CryptographicKeyItemRepository;
import com.czertainly.core.dao.repository.CryptographicKeyRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CryptographicKeyEventHistoryService;
import com.czertainly.core.service.CryptographicOperationService;
import com.czertainly.core.service.PermissionEvaluator;
import com.czertainly.core.service.TokenInstanceService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.CertificateRequestUtils;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class CryptographicOperationServiceImpl implements CryptographicOperationService {

    private static final Logger logger = LoggerFactory.getLogger(CryptographicOperationServiceImpl.class);

    // --------------------------------------------------------------------------------
    // Services & API Clients
    // --------------------------------------------------------------------------------
    private TokenInstanceService tokenInstanceService;
    private CryptographicKeyEventHistoryService eventHistoryService;
    private CryptographicOperationsApiClient cryptographicOperationsApiClient;
    private PermissionEvaluator permissionEvaluator;
    private AttributeEngine attributeEngine;

    // --------------------------------------------------------------------------------
    // Repositories
    // --------------------------------------------------------------------------------
    private CryptographicKeyRepository cryptographicKeyRepository;
    private CryptographicKeyItemRepository cryptographicKeyItemRepository;

    // Setters

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
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
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.ANY, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public List<BaseAttribute> listCipherAttributes(SecuredParentUUID tokenInstanceUuid, SecuredUUID tokenProfileUuid, UUID uuid, UUID keyItemUuid, KeyAlgorithm keyAlgorithm) throws ConnectorException {
        permissionEvaluator.tokenProfile(tokenProfileUuid);
        logger.info("Requesting to list cipher attributes for Key: {} and Algorithm {}", keyItemUuid, keyAlgorithm);
        CryptographicKeyItem key = getKeyItemEntity(keyItemUuid);
        logger.debug("Key details: {}", key);
        return listEncryptionAttributes(keyAlgorithm);
    }

    @Override
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.ENCRYPT, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public EncryptDataResponseDto encryptData(SecuredParentUUID tokenInstanceUuid, SecuredUUID tokenProfileUuid, UUID uuid, UUID keyItemUuid, CipherDataRequestDto request) throws ConnectorException {
        permissionEvaluator.tokenProfile(tokenProfileUuid);
        logger.info("Request to encrypt the data using the key: {} and data: {}", keyItemUuid, request);
        CryptographicKeyItem key = getKeyItemEntity(keyItemUuid);
        verifyKeyActive(key);
        logger.debug("Key details: {}", key);
        if (request.getCipherData() == null) {
            throw new ValidationException(ValidationError.create("Cannot encrypt null data"));
        }
        if (!key.getUsage().contains(KeyUsage.SIGN)) {
            throw new ValidationException(
                    ValidationError.create(
                            "Key Usage of the certificate does not support encryption"
                    )
            );
        }
        com.czertainly.api.model.connector.cryptography.operations.CipherDataRequestDto requestDto = new com.czertainly.api.model.connector.cryptography.operations.CipherDataRequestDto();
        requestDto.setCipherData(request.getCipherData().stream().map(e -> {
                            CipherRequestData cipherRequestData = new CipherRequestData();
                            cipherRequestData.setData(base64EncodedToByteArray(e.getData()));
                            cipherRequestData.setIdentifier(e.getIdentifier());
                            return cipherRequestData;
                        })
                        .collect(Collectors.toList())
        );
        requestDto.setCipherAttributes(request.getCipherAttributes());
        logger.debug("Request to the connector: {}", requestDto);
        try {
            com.czertainly.api.model.connector.cryptography.operations.EncryptDataResponseDto response = cryptographicOperationsApiClient.encryptData(
                    key.getCryptographicKey().getTokenProfile().getTokenInstanceReference().getConnector().mapToDto(),
                    key.getCryptographicKey().getTokenProfile().getTokenInstanceReferenceUuid().toString(),
                    key.getKeyReferenceUuid().toString(),
                    requestDto
            );
            eventHistoryService.addEventHistory(KeyEvent.ENCRYPT, KeyEventStatus.SUCCESS,
                    "Encryption of data success ", null, key);
            EncryptDataResponseDto responseDto = new EncryptDataResponseDto();
            if (response.getEncryptedData() != null)
                responseDto.setEncryptedData(response.getEncryptedData().stream().map(e -> {
                    CipherResponseData cipherResponseData = new CipherResponseData();
                    cipherResponseData.setData(byteArrayToBase64Encoded(e.getData()));
                    cipherResponseData.setIdentifier(e.getIdentifier());
                    cipherResponseData.setDetails(e.getDetails());
                    return cipherResponseData;
                }).collect(Collectors.toList()));
            return responseDto;
        } catch (Exception e) {
            eventHistoryService.addEventHistory(KeyEvent.ENCRYPT, KeyEventStatus.FAILED,
                    "Encryption of data failed ", Collections.singletonMap("exception", e.getLocalizedMessage()), key);
            throw e;
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.DECRYPT, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public DecryptDataResponseDto decryptData(SecuredParentUUID tokenInstanceUuid, SecuredUUID tokenProfileUuid, UUID uuid, UUID keyItemUuid, CipherDataRequestDto request) throws ConnectorException {
        permissionEvaluator.tokenProfile(tokenProfileUuid);
        logger.info("Decrypting using the key: {} and data: {}", keyItemUuid, request);
        CryptographicKeyItem key = getKeyItemEntity(keyItemUuid);
        verifyKeyActive(key);
        logger.debug("Key details: {}", key);
        if (request.getCipherData() == null) {
            throw new ValidationException(ValidationError.create("Cannot decrypt null data"));
        }
        if (!key.getUsage().contains(KeyUsage.SIGN)) {
            throw new ValidationException(
                    ValidationError.create(
                            "Key Usage of the certificate does not support decryption"
                    )
            );
        }
        com.czertainly.api.model.connector.cryptography.operations.CipherDataRequestDto requestDto = new com.czertainly.api.model.connector.cryptography.operations.CipherDataRequestDto();
        requestDto.setCipherData(request.getCipherData().stream().map(e -> {
                            CipherRequestData cipherRequestData = new CipherRequestData();
                            cipherRequestData.setData(base64EncodedToByteArray(e.getData()));
                            cipherRequestData.setIdentifier(e.getIdentifier());
                            return cipherRequestData;
                        })
                        .collect(Collectors.toList())
        );
        requestDto.setCipherAttributes(request.getCipherAttributes());
        logger.debug("Request to the connector: {}", requestDto);
        try {
            com.czertainly.api.model.connector.cryptography.operations.DecryptDataResponseDto response = cryptographicOperationsApiClient.decryptData(
                    key.getCryptographicKey().getTokenProfile().getTokenInstanceReference().getConnector().mapToDto(),
                    key.getCryptographicKey().getTokenProfile().getTokenInstanceReferenceUuid().toString(),
                    key.getKeyReferenceUuid().toString(),
                    requestDto);
            eventHistoryService.addEventHistory(KeyEvent.ENCRYPT, KeyEventStatus.SUCCESS,
                    "Decryption of data success ", null, key);
            DecryptDataResponseDto responseDto = new DecryptDataResponseDto();
            if (response.getDecryptedData() != null)
                responseDto.setDecryptedData(response.getDecryptedData().stream().map(e -> {
                    CipherResponseData cipherResponseData = new CipherResponseData();
                    cipherResponseData.setData(byteArrayToBase64Encoded(e.getData()));
                    cipherResponseData.setIdentifier(e.getIdentifier());
                    cipherResponseData.setDetails(e.getDetails());
                    return cipherResponseData;
                }).collect(Collectors.toList()));
            return responseDto;
        } catch (Exception e) {
            eventHistoryService.addEventHistory(KeyEvent.DECRYPT, KeyEventStatus.FAILED,
                    "Decryption of data failed ", Collections.singletonMap("exception", e.getLocalizedMessage()), key);
            throw e;
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.ANY, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public List<BaseAttribute> listSignatureAttributes(SecuredParentUUID tokenInstanceUuid, SecuredUUID tokenProfileUuid, UUID uuid, UUID keyItemUuid, KeyAlgorithm keyAlgorithm) throws ConnectorException {
        permissionEvaluator.tokenProfile(tokenProfileUuid);
        logger.info("Requesting to list the Signature Attributes for key: {} and Algorithm: {}", keyItemUuid, keyAlgorithm);
        CryptographicKeyItem key = getKeyItemEntity(keyItemUuid);
        logger.debug("Key details: {}", key);
        return listSignatureAttributes(key.getKeyAlgorithm());
    }

    @Override
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.SIGN, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public SignDataResponseDto signData(SecuredParentUUID tokenInstanceUuid, SecuredUUID tokenProfileUuid, UUID uuid, UUID keyItemUuid, SignDataRequestDto request) throws ConnectorException {
        permissionEvaluator.tokenProfile(tokenProfileUuid);
        logger.info("Signing the data: {} using the key: {}", request, keyItemUuid);
        CryptographicKeyItem key = getKeyItemEntity(keyItemUuid);
        verifyKeyActive(key);
        logger.debug("Key details: {}", key);
        if (request.getData() == null) {
            throw new ValidationException(ValidationError.create("Cannot sign empty data"));
        }
        if (!key.getUsage().contains(KeyUsage.SIGN)) {
            throw new ValidationException(
                    ValidationError.create(
                            "Key Usage of the certificate does not support signing"
                    )
            );
        }
        validateSignatureAttributes(key.getKeyAlgorithm(), request.getSignatureAttributes());
        com.czertainly.api.model.connector.cryptography.operations.SignDataRequestDto requestDto = new com.czertainly.api.model.connector.cryptography.operations.SignDataRequestDto();
        requestDto.setSignatureAttributes(request.getSignatureAttributes());
        requestDto.setData(request.getData().stream().map(e -> {
                            com.czertainly.api.model.connector.cryptography.operations.data.SignatureRequestData signatureRequestData = new com.czertainly.api.model.connector.cryptography.operations.data.SignatureRequestData();
                            signatureRequestData.setData(base64EncodedToByteArray(e.getData()));
                            signatureRequestData.setIdentifier(e.getIdentifier());
                            return signatureRequestData;
                        })
                        .collect(Collectors.toList())
        );
        logger.debug("Request to the connector: {}", requestDto);
        try {
            com.czertainly.api.model.connector.cryptography.operations.SignDataResponseDto response = cryptographicOperationsApiClient.signData(
                    key.getCryptographicKey().getTokenProfile().getTokenInstanceReference().getConnector().mapToDto(),
                    key.getCryptographicKey().getTokenProfile().getTokenInstanceReferenceUuid().toString(),
                    key.getKeyReferenceUuid().toString(),
                    requestDto
            );
            eventHistoryService.addEventHistory(KeyEvent.ENCRYPT, KeyEventStatus.SUCCESS,
                    "Signing data success ", null, key);
            SignDataResponseDto responseDto = new SignDataResponseDto();
            if (response.getSignatures() != null) responseDto.setSignatures(response.getSignatures().stream().map(e -> {
                SignatureResponseData signatureResponseData = new SignatureResponseData();
                signatureResponseData.setData(byteArrayToBase64Encoded(e.getData()));
                signatureResponseData.setIdentifier(e.getIdentifier());
                signatureResponseData.setDetails(e.getDetails());
                return signatureResponseData;
            }).collect(Collectors.toList()));
            return responseDto;
        } catch (Exception e) {
            eventHistoryService.addEventHistory(KeyEvent.SIGN, KeyEventStatus.FAILED,
                    "Signing of data failed ", Collections.singletonMap("exception", e.getLocalizedMessage()), key);
            throw e;
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.VERIFY, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public VerifyDataResponseDto verifyData(SecuredParentUUID tokenInstanceUuid, SecuredUUID tokenProfileUuid, UUID uuid, UUID keyItemUuid, VerifyDataRequestDto request) throws ConnectorException {
        permissionEvaluator.tokenProfile(tokenProfileUuid);
        logger.info("Request to verify data: {} for the key: {}", request, keyItemUuid);
        CryptographicKeyItem key = getKeyItemEntity(keyItemUuid);
        verifyKeyActive(key);
        logger.debug("Key details: {}", key);
        if (request.getSignatures() == null) {
            throw new ValidationException(ValidationError.create("Cannot verify empty data"));
        }
        validateSignatureAttributes(key.getKeyAlgorithm(), request.getSignatureAttributes());
        if (!key.getUsage().contains(KeyUsage.VERIFY)) {
            throw new ValidationException(
                    ValidationError.create(
                            "Key Usage of the certificate does not support verification"
                    )
            );
        }
        com.czertainly.api.model.connector.cryptography.operations.VerifyDataRequestDto requestDto = new com.czertainly.api.model.connector.cryptography.operations.VerifyDataRequestDto();
        requestDto.setSignatureAttributes(request.getSignatureAttributes());
        if (request.getData() != null) requestDto.setData(request.getData().stream().map(e -> {
                            com.czertainly.api.model.connector.cryptography.operations.data.SignatureRequestData signatureRequestData = new com.czertainly.api.model.connector.cryptography.operations.data.SignatureRequestData();
                            signatureRequestData.setData(base64EncodedToByteArray(e.getData()));
                            signatureRequestData.setIdentifier(e.getIdentifier());
                            return signatureRequestData;
                        })
                        .collect(Collectors.toList())
        );
        requestDto.setSignatures(request.getSignatures().stream().map(e -> {
                            com.czertainly.api.model.connector.cryptography.operations.data.SignatureRequestData signatureRequestData = new com.czertainly.api.model.connector.cryptography.operations.data.SignatureRequestData();
                            signatureRequestData.setData(base64EncodedToByteArray(e.getData()));
                            signatureRequestData.setIdentifier(e.getIdentifier());
                            return signatureRequestData;
                        })
                        .collect(Collectors.toList())
        );
        logger.debug("Request to the connector: {}", requestDto);
        try {
            com.czertainly.api.model.connector.cryptography.operations.VerifyDataResponseDto response = cryptographicOperationsApiClient.verifyData(
                    key.getCryptographicKey().getTokenProfile().getTokenInstanceReference().getConnector().mapToDto(),
                    key.getCryptographicKey().getTokenProfile().getTokenInstanceReferenceUuid().toString(),
                    key.getKeyReferenceUuid().toString(),
                    requestDto
            );
            eventHistoryService.addEventHistory(KeyEvent.ENCRYPT, KeyEventStatus.SUCCESS,
                    "Verification of data completed ", null, key);
            VerifyDataResponseDto responseDto = new VerifyDataResponseDto();
            if (response.getVerifications() != null)
                responseDto.setVerifications(response.getVerifications().stream().map(e -> {
                    VerificationResponseData verifyDataResponseDto = new VerificationResponseData();
                    verifyDataResponseDto.setResult(e.isResult());
                    verifyDataResponseDto.setIdentifier(e.getIdentifier());
                    verifyDataResponseDto.setDetails(e.getDetails());
                    return verifyDataResponseDto;
                }).collect(Collectors.toList()));
            return responseDto;
        } catch (Exception e) {
            eventHistoryService.addEventHistory(KeyEvent.ENCRYPT, KeyEventStatus.FAILED,
                    "Verification of data failed ", Collections.singletonMap("exception", e.getLocalizedMessage()), key);
            throw e;
        }
    }

    @Override
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
    @ExternalAuthorization(resource = Resource.TOKEN, action = ResourceAction.DETAIL)
    public RandomDataResponseDto randomData(SecuredUUID tokenInstanceUuid, RandomDataRequestDto request) throws ConnectorException {
        logger.info("Requesting attributes for random generation for token Instance: {}", tokenInstanceUuid);
        TokenInstanceReference tokenInstanceReference = tokenInstanceService.getTokenInstanceEntity(tokenInstanceUuid);
        logger.debug("Token Instance details: {}", tokenInstanceReference);
        com.czertainly.api.model.connector.cryptography.operations.RandomDataRequestDto requestDto = new com.czertainly.api.model.connector.cryptography.operations.RandomDataRequestDto();
        requestDto.setAttributes(request.getAttributes());
        requestDto.setLength(request.getLength());
        logger.debug("Request to the connector: {}", requestDto);
        com.czertainly.api.model.connector.cryptography.operations.RandomDataResponseDto response = cryptographicOperationsApiClient.randomData(
                tokenInstanceReference.getConnector().mapToDto(),
                tokenInstanceReference.getTokenInstanceUuid(),
                requestDto
        );
        RandomDataResponseDto responseDto = new RandomDataResponseDto();
        responseDto.setData(byteArrayToBase64Encoded(response.getData()));
        return responseDto;
    }

    @Override
    public String generateCsr(UUID keyUuid, UUID tokenProfileUuid, X500Principal principal, List<RequestAttributeDto> signatureAttributes) throws NotFoundException, NoSuchAlgorithmException, InvalidKeySpecException, IOException, AttributeException {
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

        if (!key.getTokenProfile().getUuid().equals(tokenProfileUuid)) {
            throw new ValidationException(
                    ValidationError.create(
                            "Key and Token Profile are not associated to each other"
                    )
            );
        }
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
        if (privateKeyItem == null || publicKeyItem == null) {
            throw new ValidationException(
                    ValidationError.create(
                            "Selected item does not contain the complete keypair"
                    )
            );
        }
        verifyKeyActive(privateKeyItem);
        verifyKeyActive(publicKeyItem);

        // validate and update definitions of signature attributes with attribute engine
        List<BaseAttribute> definitions = listSignatureAttributes(privateKeyItem.getKeyAlgorithm());
        attributeEngine.validateUpdateDataAttributes(null, AttributeOperation.CERTIFICATE_REQUEST_SIGN, definitions, signatureAttributes);

        // Generate the CSR
        return generateCsr(
                principal,
                publicKeyItem.getKeyData(),
                privateKeyItem,
                publicKeyItem,
                signatureAttributes
        );
    }

    private void verifyKeyActive(CryptographicKeyItem keyItem) {
        if (keyItem.getState() != KeyState.ACTIVE || !keyItem.isEnabled()) {
            throw new ValidationException(ValidationError.create("Key needs to be " + KeyState.ACTIVE.getLabel() + " and enabled."));
        }
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

    private String generateCsr(X500Principal principal, String key, CryptographicKeyItem privateKeyItem, CryptographicKeyItem publicKeyItem, List<RequestAttributeDto> signatureAttributes) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        // Build bouncy castle p10 builder
        PKCS10CertificationRequestBuilder p10Builder = new JcaPKCS10CertificationRequestBuilder(
                principal,
                CertificateRequestUtils.publicKeyObjectFromString(key, publicKeyItem.getKeyAlgorithm().getCode())
        );

        // Assign the custom signer to sign the CSR with the private key from the cryptography provider
        ContentSigner signer = new TokenContentSigner(
                cryptographicOperationsApiClient,
                privateKeyItem.getCryptographicKey().getTokenInstanceReference().getConnector().mapToDto(),
                privateKeyItem.getCryptographicKey().getTokenInstanceReferenceUuid(),
                privateKeyItem.getKeyReferenceUuid(),
                publicKeyItem.getKeyReferenceUuid(),
                publicKeyItem.getKeyData(),
                publicKeyItem.getKeyAlgorithm(),
                signatureAttributes
        );

        // Build the CSR with the DN generated and the signer
        PKCS10CertificationRequest csr = p10Builder.build(signer);

        // Convert the data from byte array to string
        return CertificateRequestUtils.byteArrayCsrToString(csr.getEncoded());
    }

    private List<BaseAttribute> listSignatureAttributes(KeyAlgorithm keyAlgorithm) {
        // we need to list based on the key algorithm
        switch (keyAlgorithm) {
            case RSA -> {
                return RsaSignatureAttributes.getRsaSignatureAttributes();
            }
            case ECDSA -> {
                return EcdsaSignatureAttributes.getEcdsaSignatureAttributes();
            }
            case FALCON, DILITHIUM, SPHINCSPLUS -> {
                return List.of();
            }
            default -> throw new ValidationException(
                    ValidationError.create(
                            "Cryptographic key algorithm not supported"
                    )
            );
        }
    }


    private List<BaseAttribute> listEncryptionAttributes(KeyAlgorithm keyAlgorithm) {
        switch (keyAlgorithm) {
            case RSA -> {
                return RsaEncryptionAttributes.getRsaEncryptionAttributes();
            }
            default -> throw new ValidationException(
                    ValidationError.create(
                            "Cryptographic key algorithm not supported"
                    )
            );
        }
    }

    private boolean validateSignatureAttributes(KeyAlgorithm keyAlgorithm, List<RequestAttributeDto> attributes) throws NotFoundException {
        if (attributes == null) {
            return false;
        }

        switch (keyAlgorithm) {
            case RSA ->
                    AttributeDefinitionUtils.validateAttributes(RsaSignatureAttributes.getRsaSignatureAttributes(), attributes);
            case ECDSA ->
                    AttributeDefinitionUtils.validateAttributes(EcdsaSignatureAttributes.getEcdsaSignatureAttributes(), attributes);
            case FALCON, DILITHIUM, SPHINCSPLUS -> {
            }
            default -> throw new ValidationException(
                    ValidationError.create(
                            "Cryptographic key algorithm not supported"
                    )
            );
        }

        return true;
    }

    private byte[] base64EncodedToByteArray(String encoded) {
        if (encoded == null) {
            return null;
        }
        return Base64.getDecoder().decode(encoded.getBytes(StandardCharsets.UTF_8));
    }

    private String byteArrayToBase64Encoded(byte[] input) {
        if (input == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(input);
    }
}
