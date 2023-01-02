package com.czertainly.core.service.impl;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.CryptographicOperationException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
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
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.repository.CryptographicKeyRepository;
import com.czertainly.core.service.CryptographicOperationService;
import com.czertainly.core.service.MetadataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Transactional
//TODO Access Control related changes and determination of Access Control Objects
public class CryptographicOperationServiceImpl implements CryptographicOperationService {

    private static final Logger logger = LoggerFactory.getLogger(CryptographicOperationServiceImpl.class);

    // --------------------------------------------------------------------------------
    // Services & API Clients
    // --------------------------------------------------------------------------------
    private MetadataService metadataService;
    private CryptographicOperationsApiClient cryptographicOperationsApiClient;

    // --------------------------------------------------------------------------------
    // Repositories
    // --------------------------------------------------------------------------------
    private CryptographicKeyRepository cryptographicKeyRepository;

    // Setters
    @Autowired
    public void setMetadataService(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Autowired
    public void setCryptographicOperationsApiClient(CryptographicOperationsApiClient cryptographicOperationsApiClient) {
        this.cryptographicOperationsApiClient = cryptographicOperationsApiClient;
    }

    @Autowired
    public void setCryptographicKeyRepository(CryptographicKeyRepository cryptographicKeyRepository) {
        this.cryptographicKeyRepository = cryptographicKeyRepository;
    }

    // ----------------------------------------------------------------------------------------------
    // Service Implementations
    // ----------------------------------------------------------------------------------------------

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    public List<BaseAttribute> listCipherAttributes(UUID uuid, CryptographicAlgorithm algorithm) throws ConnectorException {
        logger.info("Requesting to list cipher attributes for Key: {} and Algorithm {}", uuid, algorithm);
        CryptographicKey key = getKeyEntity(uuid);
        logger.debug("Key details: {}", key);
        return cryptographicOperationsApiClient.listCipherAttributes(
                key.getTokenProfile().getTokenInstanceReference().getConnector().mapToDto(),
                key.getTokenProfile().getTokenInstanceReferenceUuid().toString(),
                algorithm);
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.ENCRYPT)
    public EncryptDataResponseDto encryptData(UUID uuid, CipherDataRequestDto request) throws ConnectorException, CryptographicOperationException {
        logger.info("Request to encrypt the data using the key: {} and data: {}", uuid, request);
        CryptographicKey key = getKeyEntity(uuid);
        logger.debug("Key details: {}", key);
        if (request.getCipherData() == null) {
            throw new ValidationException(ValidationError.create("Cannot encrypt null data"));
        }
        com.czertainly.api.model.connector.cryptography.operations.CipherDataRequestDto requestDto = new com.czertainly.api.model.connector.cryptography.operations.CipherDataRequestDto();
        requestDto.setCipherData(request.getCipherData());
        requestDto.setCipherAttributes(request.getCipherAttributes());
        logger.debug("Request to the connector: {}", requestDto);
        return cryptographicOperationsApiClient.encryptData(
                key.getTokenProfile().getTokenInstanceReference().getConnector().mapToDto(),
                key.getTokenProfile().getTokenInstanceReferenceUuid().toString(),
                requestDto
        );
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.DECRYPT)
    public DecryptDataResponseDto decryptData(UUID uuid, CipherDataRequestDto request) throws ConnectorException, CryptographicOperationException {
        logger.info("Decrypting using the key: {} and data: {}", uuid, request);
        CryptographicKey key = getKeyEntity(uuid);
        logger.debug("Key details: {}", key);
        if (request.getCipherData() == null) {
            throw new ValidationException(ValidationError.create("Cannot decrypt null data"));
        }
        com.czertainly.api.model.connector.cryptography.operations.CipherDataRequestDto requestDto = new com.czertainly.api.model.connector.cryptography.operations.CipherDataRequestDto();
        requestDto.setCipherData(request.getCipherData());
        requestDto.setCipherAttributes(request.getCipherAttributes());
        logger.debug("Request to the connector: {}", requestDto);
        return cryptographicOperationsApiClient.decryptData(
                key.getTokenProfile().getTokenInstanceReference().getConnector().mapToDto(),
                key.getTokenProfile().getTokenInstanceReferenceUuid().toString(),
                requestDto);
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    public List<BaseAttribute> listSignatureAttributes(UUID uuid, CryptographicAlgorithm algorithm) throws ConnectorException {
        logger.info("Requesting to list the Signature Attributes for key: {} and Algorithm: {}", uuid, algorithm);
        CryptographicKey key = getKeyEntity(uuid);
        logger.debug("Key details: {}", key);
        return cryptographicOperationsApiClient.listSignatureAttributes(
                key.getTokenProfile().getTokenInstanceReference().getConnector().mapToDto(),
                key.getTokenProfile().getTokenInstanceReferenceUuid().toString(),
                algorithm
        );
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.SIGN)
    public SignDataResponseDto signData(UUID uuid, SignDataRequestDto request) throws ConnectorException, CryptographicOperationException {
        logger.info("Signing the data: {} using the key: {}", request, uuid);
        CryptographicKey key = getKeyEntity(uuid);
        logger.debug("Key details: {}", key);
        if (Stream.of(request.getData(), request.getDigestedData()).allMatch(Objects::isNull)) {
            throw new ValidationException(ValidationError.create("Cannot sign empty data"));
        }
        com.czertainly.api.model.connector.cryptography.operations.SignDataRequestDto requestDto = new com.czertainly.api.model.connector.cryptography.operations.SignDataRequestDto();
        requestDto.setDigestedData(request.getDigestedData());
        requestDto.setSignatureAttributes(request.getSignatureAttributes());
        requestDto.setData(request.getData());
        requestDto.setKeyAttributes(metadataService.getMetadata(
                key.getTokenProfile().getTokenInstanceReference().getConnectorUuid(),
                uuid,
                Resource.CRYPTOGRAPHIC_KEY
        ));
        logger.debug("Request to the connector: {}", requestDto);
        return cryptographicOperationsApiClient.signData(
                key.getTokenProfile().getTokenInstanceReference().getConnector().mapToDto(),
                key.getTokenProfile().getTokenInstanceReferenceUuid().toString(),
                requestDto
        );
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.VERIFY)
    public VerifyDataResponseDto verifyData(UUID uuid, VerifyDataRequestDto request) throws ConnectorException, CryptographicOperationException {
        logger.info("Request to verify data: {} for the key: {}", request, uuid);
        CryptographicKey key = getKeyEntity(uuid);
        logger.debug("Key details: {}", key);
        if (Stream.of(request.getData(), request.getDigestedData()).allMatch(Objects::isNull)) {
            throw new ValidationException(ValidationError.create("Cannot verify empty data"));
        }
        com.czertainly.api.model.connector.cryptography.operations.VerifyDataRequestDto requestDto = new com.czertainly.api.model.connector.cryptography.operations.VerifyDataRequestDto();
        requestDto.setDigestedData(request.getDigestedData());
        requestDto.setSignatureAttributes(request.getSignatureAttributes());
        requestDto.setData(request.getData());
        requestDto.setSignatures(request.getSignatures());
        requestDto.setKeyAttributes(metadataService.getMetadata(
                key.getTokenProfile().getTokenInstanceReference().getConnectorUuid(),
                uuid,
                Resource.CRYPTOGRAPHIC_KEY
        ));
        logger.debug("Request to the connector: {}", requestDto);
        return cryptographicOperationsApiClient.verifyData(
                key.getTokenProfile().getTokenInstanceReference().getConnector().mapToDto(),
                key.getTokenProfile().getTokenInstanceReferenceUuid().toString(),
                requestDto
        );
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    public List<BaseAttribute> listRandomAttributes(UUID uuid) throws ConnectorException {
        logger.info("Requesting attributes for random generation for key: {}", uuid);
        CryptographicKey key = getKeyEntity(uuid);
        logger.debug("Key details: {}", key);
        return cryptographicOperationsApiClient.listRandomAttributes(
                key.getTokenProfile().getTokenInstanceReference().getConnector().mapToDto(),
                key.getTokenProfile().getTokenInstanceReferenceUuid().toString()
        );
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    public RandomDataResponseDto randomData(UUID uuid, RandomDataRequestDto request) throws ConnectorException, CryptographicOperationException {
        logger.info("Request for random data generation: {} and data: {}", uuid, request);
        CryptographicKey key = getKeyEntity(uuid);
        logger.debug("Key details: {}", key);
        com.czertainly.api.model.connector.cryptography.operations.RandomDataRequestDto requestDto = new com.czertainly.api.model.connector.cryptography.operations.RandomDataRequestDto();
        requestDto.setAttributes(request.getAttributes());
        requestDto.setLength(request.getLength());
        logger.debug("Request to the connector: {}", requestDto);
        return cryptographicOperationsApiClient.randomData(
                key.getTokenProfile().getTokenInstanceReference().getConnector().mapToDto(),
                key.getTokenProfile().getTokenInstanceReferenceUuid().toString(),
                requestDto
        );
    }

    private CryptographicKey getKeyEntity(UUID uuid) throws NotFoundException {
        logger.debug("UUID of the key to get the entity: {}", uuid);
        CryptographicKey key = cryptographicKeyRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(CryptographicKey.class, uuid));
        if (key.getTokenProfile().getTokenInstanceReference() == null) {
            throw new NotFoundException("Token Instance associated with the Key is not found");
        }
        if (key.getTokenProfile().getTokenInstanceReference().getConnector() == null) {
            throw new NotFoundException(("Connector associated to the Key is not found"));
        }
        logger.debug("Key Instance: {}", key);
        return key;
    }
}
