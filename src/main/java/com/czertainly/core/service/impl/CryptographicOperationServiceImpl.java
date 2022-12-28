package com.czertainly.core.service.impl;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.api.clients.cryptography.KeyManagementApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.CryptographicOperationException;
import com.czertainly.api.exception.NotFoundException;
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
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.cryptography.key.KeyDetailDto;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceDetailDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.repository.CryptographicKeyRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.AttributeService;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.service.CryptographicKeyService;
import com.czertainly.core.service.CryptographicOperationService;
import com.czertainly.core.service.MetadataService;
import com.czertainly.core.service.TokenInstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
//TODO Access Control related changes and determination of Access Control Objects
public class CryptographicOperationServiceImpl implements CryptographicOperationService {

    private static final Logger logger = LoggerFactory.getLogger(CryptographicOperationServiceImpl.class);

    // --------------------------------------------------------------------------------
    // Services & API Clients
    // --------------------------------------------------------------------------------
    private AttributeService attributeService;
    private MetadataService metadataService;
    private TokenInstanceService tokenInstanceService;
    private CryptographicKeyService cryptographicKeyService;
    private ConnectorService connectorService;
    private CryptographicOperationsApiClient cryptographicOperationsApiClient;

    // --------------------------------------------------------------------------------
    // Repositories
    // --------------------------------------------------------------------------------
    private CryptographicKeyRepository cryptographicKeyRepository;

    // Setters

    @Autowired
    public void setAttributeService(AttributeService attributeService) {
        this.attributeService = attributeService;
    }

    @Autowired
    public void setMetadataService(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Autowired
    public void setTokenInstanceService(TokenInstanceService tokenInstanceService) {
        this.tokenInstanceService = tokenInstanceService;
    }

    @Autowired
    public void setCryptographicKeyService(CryptographicKeyService cryptographicKeyService) {
        this.cryptographicKeyService = cryptographicKeyService;
    }

    @Autowired
    public void setConnectorService(ConnectorService connectorService) {
        this.connectorService = connectorService;
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
        CryptographicKey key = getKeyEntity(uuid);
        return cryptographicOperationsApiClient.listCipherAttributes(
                key.getTokenInstanceReference().getConnector().mapToDto(),
                key.getTokenInstanceReferenceUuid().toString(),
                algorithm);
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.ENCRYPT)
    public EncryptDataResponseDto encryptData(UUID uuid, CipherDataRequestDto request) throws ConnectorException, CryptographicOperationException {
        CryptographicKey key = getKeyEntity(uuid);
        com.czertainly.api.model.connector.cryptography.operations.CipherDataRequestDto requestDto = new com.czertainly.api.model.connector.cryptography.operations.CipherDataRequestDto();
        requestDto.setCipherData(request.getCipherData());
        requestDto.setCipherAttributes(request.getCipherAttributes());
        return cryptographicOperationsApiClient.encryptData(
                key.getTokenInstanceReference().getConnector().mapToDto(),
                key.getTokenInstanceReferenceUuid().toString(),
                requestDto
        );
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.DECRYPT)
    public DecryptDataResponseDto decryptData(UUID uuid, CipherDataRequestDto request) throws ConnectorException, CryptographicOperationException {
        CryptographicKey key = getKeyEntity(uuid);
        com.czertainly.api.model.connector.cryptography.operations.CipherDataRequestDto requestDto = new com.czertainly.api.model.connector.cryptography.operations.CipherDataRequestDto();
        requestDto.setCipherData(request.getCipherData());
        requestDto.setCipherAttributes(request.getCipherAttributes());
        return cryptographicOperationsApiClient.decryptData(
                key.getTokenInstanceReference().getConnector().mapToDto(),
                key.getTokenInstanceReferenceUuid().toString(),
                requestDto);
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    public List<BaseAttribute> listSignatureAttributes(UUID uuid, CryptographicAlgorithm algorithm) throws ConnectorException {
        CryptographicKey key = getKeyEntity(uuid);
        return cryptographicOperationsApiClient.listSignatureAttributes(
                key.getTokenInstanceReference().getConnector().mapToDto(),
                key.getTokenInstanceReferenceUuid().toString(),
                algorithm
        );
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.SIGN)
    public SignDataResponseDto signData(UUID uuid, SignDataRequestDto request) throws ConnectorException, CryptographicOperationException {
        CryptographicKey key = getKeyEntity(uuid);
        com.czertainly.api.model.connector.cryptography.operations.SignDataRequestDto requestDto = new com.czertainly.api.model.connector.cryptography.operations.SignDataRequestDto();
        requestDto.setDigestedData(request.getDigestedData());
        requestDto.setSignatureAttributes(request.getSignatureAttributes());
        requestDto.setData(request.getData());
        requestDto.setKeyAttributes(metadataService.getMetadata(
                key.getTokenInstanceReference().getConnectorUuid(),
                uuid,
                Resource.CRYPTOGRAPHIC_KEY
        ));
        return cryptographicOperationsApiClient.signData(
                key.getTokenInstanceReference().getConnector().mapToDto(),
                key.getTokenInstanceReferenceUuid().toString(),
                requestDto
        );
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.VERIFY)
    public VerifyDataResponseDto verifyData(UUID uuid, VerifyDataRequestDto request) throws ConnectorException, CryptographicOperationException {
        CryptographicKey key = getKeyEntity(uuid);
        com.czertainly.api.model.connector.cryptography.operations.VerifyDataRequestDto requestDto = new com.czertainly.api.model.connector.cryptography.operations.VerifyDataRequestDto();
        requestDto.setDigestedData(request.getDigestedData());
        requestDto.setSignatureAttributes(request.getSignatureAttributes());
        requestDto.setData(request.getData());
        requestDto.setSignatures(request.getSignatures());
        requestDto.setKeyAttributes(metadataService.getMetadata(
                key.getTokenInstanceReference().getConnectorUuid(),
                uuid,
                Resource.CRYPTOGRAPHIC_KEY
        ));
        return cryptographicOperationsApiClient.verifyData(
                key.getTokenInstanceReference().getConnector().mapToDto(),
                key.getTokenInstanceReferenceUuid().toString(),
                requestDto
        );
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    public List<BaseAttribute> listRandomAttributes(UUID uuid) throws ConnectorException {
        CryptographicKey key = getKeyEntity(uuid);
        return cryptographicOperationsApiClient.listRandomAttributes(
                key.getTokenInstanceReference().getConnector().mapToDto(),
                key.getTokenInstanceReferenceUuid().toString()
        );
    }

    @Override
    @AuditLogged(originator = ObjectType.CRYPTOGRAPHIC_OPERATIONS, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    public RandomDataResponseDto randomData(UUID uuid, RandomDataRequestDto request) throws ConnectorException, CryptographicOperationException {
        CryptographicKey key = getKeyEntity(uuid);
        com.czertainly.api.model.connector.cryptography.operations.RandomDataRequestDto requestDto = new com.czertainly.api.model.connector.cryptography.operations.RandomDataRequestDto();
        requestDto.setAttributes(request.getAttributes());
        requestDto.setLength(request.getLength());
        return cryptographicOperationsApiClient.randomData(
                key.getTokenInstanceReference().getConnector().mapToDto(),
                key.getTokenInstanceReferenceUuid().toString(),
                requestDto
        );
    }

    private CryptographicKey getKeyEntity(UUID uuid) throws NotFoundException {
        CryptographicKey key = cryptographicKeyRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(CryptographicKey.class, uuid));
        if(key.getTokenInstanceReference() == null ){
            throw new NotFoundException("Token Instance associated with the Key is not found");
        }
        if(key.getTokenInstanceReference().getConnector() == null) {
            throw new NotFoundException(("Connector associated to the Key is not found"));
        }
        return key;
    }
}
