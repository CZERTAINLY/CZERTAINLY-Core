package com.czertainly.core.service.impl;

import com.czertainly.api.clients.cryptography.KeyManagementApiClient;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.cryptography.key.KeyRequestDto;
import com.czertainly.api.model.client.cryptography.key.KeyRequestType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.connector.cryptography.key.CreateKeyRequestDto;
import com.czertainly.api.model.connector.cryptography.key.KeyData;
import com.czertainly.api.model.connector.cryptography.key.KeyDataResponseDto;
import com.czertainly.api.model.connector.cryptography.key.KeyPairDataResponseDto;
import com.czertainly.api.model.connector.cryptography.key.SecretKeyDataResponseDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cryptography.key.KeyDetailDto;
import com.czertainly.api.model.core.cryptography.key.KeyDto;
import com.czertainly.api.model.core.cryptography.tokenprofile.TokenProfileDetailDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.CryptographicKeyContent;
import com.czertainly.core.dao.entity.TokenInstanceReference;
import com.czertainly.core.dao.entity.TokenProfile;
import com.czertainly.core.dao.repository.CryptographicKeyContentRepository;
import com.czertainly.core.dao.repository.CryptographicKeyRepository;
import com.czertainly.core.dao.repository.TokenProfileRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AttributeService;
import com.czertainly.core.service.CryptographicKeyService;
import com.czertainly.core.service.MetadataService;
import com.czertainly.core.service.TokenInstanceService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class CryptographicKeyServiceImpl implements CryptographicKeyService {

    private static final Logger logger = LoggerFactory.getLogger(CryptographicKeyServiceImpl.class);

    // --------------------------------------------------------------------------------
    // Services & API Clients
    // --------------------------------------------------------------------------------
    private AttributeService attributeService;
    private MetadataService metadataService;
    private KeyManagementApiClient keyManagementApiClient;
    private TokenInstanceService tokenInstanceService;
    // --------------------------------------------------------------------------------
    // Repositories
    // --------------------------------------------------------------------------------
    private CryptographicKeyRepository cryptographicKeyRepository;
    private CryptographicKeyContentRepository cryptographicKeyContentRepository;
    private TokenProfileRepository tokenProfileRepository;

    @Autowired
    public void setAttributeService(AttributeService attributeService) {
        this.attributeService = attributeService;
    }

    @Autowired
    public void setMetadataService(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Autowired
    public void setKeyManagementApiClient(KeyManagementApiClient keyManagementApiClient) {
        this.keyManagementApiClient = keyManagementApiClient;
    }

    @Autowired
    public void setTokenInstanceService(TokenInstanceService tokenInstanceService) {
        this.tokenInstanceService = tokenInstanceService;
    }

    @Autowired
    public void setCryptographicKeyRepository(CryptographicKeyRepository cryptographicKeyRepository) {
        this.cryptographicKeyRepository = cryptographicKeyRepository;
    }

    @Autowired
    public void setCryptographicKeyContentRepository(CryptographicKeyContentRepository cryptographicKeyContentRepository) {
        this.cryptographicKeyContentRepository = cryptographicKeyContentRepository;
    }

    @Autowired
    public void setTokenProfileRepository(TokenProfileRepository tokenProfileRepository) {
        this.tokenProfileRepository = tokenProfileRepository;
    }

    // ----------------------------------------------------------------------------------------------
    // Service Implementations
    // ----------------------------------------------------------------------------------------------

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CRYPTOGRAPHIC_KEY, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.LIST, parentResource = Resource.TOKEN_PROFILE, parentAction = ResourceAction.LIST)
    public List<KeyDto> listKeys(Optional<String> tokenProfileUuid, SecurityFilter filter) {
        logger.info("Requesting key list for Token profile with UUID {}", tokenProfileUuid);
        filter.setParentRefProperty("tokenInstanceReferenceUuid");
        return cryptographicKeyRepository.findUsingSecurityFilter(filter)
                .stream()
                .map(CryptographicKey::mapToDto)
                .collect(Collectors.toList()
                );
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CRYPTOGRAPHIC_KEY, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.DETAIL)
    public KeyDetailDto getKey(SecuredParentUUID tokenProfileUuid, String uuid) throws NotFoundException {
        logger.info("Requesting details of the Key with UUID {} for Token profile {}", uuid, tokenProfileUuid);
        CryptographicKey key = getCryptographicKeyEntity(UUID.fromString(uuid));
        KeyDetailDto dto = key.mapToDetailDto();
        logger.debug("Key details: {}", dto);
        dto.setCustomAttributes(
                attributeService.getCustomAttributesWithValues(
                        key.getUuid(),
                        Resource.CRYPTOGRAPHIC_KEY
                )
        );
        dto.setKeyAttributes(
                metadataService.getMetadata(
                        key.getTokenProfile().getTokenInstanceReference().getUuid(),
                        key.getUuid(),
                        Resource.CRYPTOGRAPHIC_KEY
                )
        );
        logger.debug("Key details with attributes {}", dto);
        return dto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CRYPTOGRAPHIC_KEY, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.CREATE)
    public KeyDetailDto createKey(SecuredParentUUID tokenProfileUuid, KeyRequestType type, KeyRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException {
        logger.error("Creating a new key for Token profile {}. Input: {}", tokenProfileUuid, request);
        if (cryptographicKeyRepository.findByName(request.getName()).isPresent()) {
            logger.error("Key with same name already exists");
            throw new AlreadyExistException("Existing Key with same already exists");
        }
        if (request.getName() == null) {
            logger.error("Name is empty. Cannot create key without name");
            throw new ValidationException("Name is required for creating a new Key");
        }
        TokenProfile tokenProfile = tokenProfileRepository.findByUuid(
                        tokenProfileUuid.getValue())
                .orElseThrow(
                        () -> new NotFoundException(
                                TokenInstanceReference.class,
                                tokenProfileUuid
                        )
                );
        TokenProfileDetailDto dto = tokenProfile.mapToDetailDto();
        logger.debug("Token profile detail: {}", dto);
        Connector connector = tokenProfile.getTokenInstanceReference().getConnector();
        logger.debug("Connector details: {}", connector);
        List<DataAttribute> attributes = mergeAndValidateAttributes(
                type,
                tokenProfile.getTokenInstanceReference(),
                request.getAttributes()
        );
        logger.debug("Merged attributes for the request: {}", attributes);
        CreateKeyRequestDto createKeyRequestDto = new CreateKeyRequestDto();
        createKeyRequestDto.setCreateKeyAttributes(
                AttributeDefinitionUtils.getClientAttributes(
                        attributes
                )
        );
        createKeyRequestDto.setTokenProfileAttributes(
                AttributeDefinitionUtils.getClientAttributes(
                        dto.getAttributes()
                )
        );

        CryptographicKey key;
        if (type.equals(KeyRequestType.KEY_PAIR)) {
            key = createKeyTypeOfKeyPair(
                    connector,
                    tokenProfile,
                    request,
                    createKeyRequestDto
            );
        } else {
            key = createKeyTypeOfSecret(
                    connector,
                    tokenProfile,
                    request,
                    createKeyRequestDto
            );
        }

        attributeService.createAttributeContent(
                key.getUuid(),
                request.getCustomAttributes(),
                Resource.CRYPTOGRAPHIC_KEY
        );

        logger.debug("Key creation is successful. UUID is {}", key.getUuid());
        KeyDetailDto keyDetailDto = key.mapToDetailDto();
        keyDetailDto.setCustomAttributes(
                attributeService.getCustomAttributesWithValues(
                        key.getUuid(),
                        Resource.CRYPTOGRAPHIC_KEY
                )
        );
        keyDetailDto.setCustomAttributes(
                attributeService.getCustomAttributesWithValues(
                        key.getUuid(),
                        Resource.CRYPTOGRAPHIC_KEY
                )
        );
        logger.debug("Key details: {}", keyDetailDto);
        return keyDetailDto;
    }

    private CryptographicKey createKeyTypeOfKeyPair(Connector connector, TokenProfile tokenProfile, KeyRequestDto request, CreateKeyRequestDto createKeyRequestDto) throws ConnectorException {
        KeyPairDataResponseDto response = keyManagementApiClient.createKeyPair(
                connector.mapToDto(),
                tokenProfile.getTokenInstanceReference().getTokenInstanceUuid(),
                createKeyRequestDto
        );
        logger.debug("Response from the connector for the new Key creation: {}", response);
        CryptographicKey key = createKeyEntity(
                request.getName(),
                request.getDescription(),
                tokenProfile.getUuid()
        );
        createKeyContent(response.getPrivateKey().getUuid(), response.getPrivateKey().getKeyData(), key);
        createKeyContent(response.getPublicKey().getUuid(), response.getPublicKey().getKeyData(), key);

        metadataService.createMetadataDefinitions(
                connector.getUuid(),
                response.getPrivateKey().getKeyData().getMetadata()
        );
        metadataService.createMetadata(
                connector.getUuid(),
                key.getUuid(),
                key.getUuid(),
                request.getName(),
                response.getPrivateKey().getKeyData().getMetadata(),
                Resource.CRYPTOGRAPHIC_KEY,
                Resource.CRYPTOGRAPHIC_KEY
        );

        metadataService.createMetadataDefinitions(
                connector.getUuid(),
                response.getPublicKey().getKeyData().getMetadata()
        );
        metadataService.createMetadata(
                connector.getUuid(),
                key.getUuid(),
                key.getUuid(),
                request.getName(),
                response.getPublicKey().getKeyData().getMetadata(),
                Resource.CRYPTOGRAPHIC_KEY,
                Resource.CRYPTOGRAPHIC_KEY
        );
        return key;
    }

    private CryptographicKey createKeyTypeOfSecret(Connector connector, TokenProfile tokenProfile, KeyRequestDto request, CreateKeyRequestDto createKeyRequestDto) throws ConnectorException {
        SecretKeyDataResponseDto response = keyManagementApiClient.createSecretKey(
                connector.mapToDto(),
                tokenProfile.getTokenInstanceReference().getTokenInstanceUuid(),
                createKeyRequestDto
        );
        logger.debug("Response from the connector for the new Key creation: {}", response);
        CryptographicKey key = createKeyEntity(
                request.getName(),
                request.getDescription(),
                tokenProfile.getUuid()
        );
        createKeyContent(response.getUuid(), response.getKeyData(), key);

        metadataService.createMetadataDefinitions(
                connector.getUuid(),
                response.getKeyData().getMetadata()
        );
        metadataService.createMetadata(
                connector.getUuid(),
                key.getUuid(),
                key.getUuid(),
                request.getName(),
                response.getKeyData().getMetadata(),
                Resource.CRYPTOGRAPHIC_KEY,
                Resource.CRYPTOGRAPHIC_KEY
        );
        return key;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CRYPTOGRAPHIC_KEY, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.DELETE)
    public void destroyKey(SecuredParentUUID tokenProfileUuid, String uuid, List<String> keyUuids) throws ConnectorException {
        logger.info("Request to destroy the key with UUID {} on token profile {}", uuid, tokenProfileUuid);
        CryptographicKey key = getCryptographicKeyEntity(UUID.fromString(uuid));

        for (String keyUuid : keyUuids) {
            CryptographicKeyContent content = cryptographicKeyContentRepository
                    .findByUuid(UUID.fromString(keyUuid))
                    .orElseThrow(
                            () -> new NotFoundException(
                                    "Sub key with the UUID " + keyUuid + " is not found",
                                    CryptographicKeyContent.class
                            )
                    );

            keyManagementApiClient.destroyKey(
                    key.getTokenProfile().getTokenInstanceReference().getConnector().mapToDto(),
                    key.getTokenProfile().getTokenInstanceReferenceUuid().toString(),
                    keyUuid
            );
            logger.info("Key destroyed in the connector. Removing from the core now");
            cryptographicKeyContentRepository.delete(content);
            attributeService.deleteAttributeContent(
                    key.getUuid(),
                    Resource.CRYPTOGRAPHIC_KEY
            );
        }
        cryptographicKeyRepository.delete(key);
        logger.info("Key destroyed: {}", uuid);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CRYPTOGRAPHIC_KEY, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.CREATE)
    public List<BaseAttribute> listCreateKeyAttributes(SecuredUUID tokenProfileUuid, KeyRequestType type) throws ConnectorException {
        logger.info("Request to list the attributes for creating a new key on Token profile: {}", tokenProfileUuid);
        TokenProfile tokenProfile = tokenProfileRepository.findByUuid(
                        tokenProfileUuid.getValue())
                .orElseThrow(
                        () -> new NotFoundException(
                                TokenInstanceReference.class,
                                tokenProfileUuid
                        )
                );
        logger.debug("Token profile details: {}", tokenProfile);
        List<BaseAttribute> attributes;
        if (type.equals(KeyRequestType.KEY_PAIR)) {
            attributes = keyManagementApiClient.listCreateKeyPairAttributes(
                    tokenProfile.getTokenInstanceReference().getConnector().mapToDto(),
                    tokenProfile.getTokenInstanceReference().getTokenInstanceUuid()
            );
        } else {
            attributes = keyManagementApiClient.listCreateSecretKeyAttributes(
                    tokenProfile.getTokenInstanceReference().getConnector().mapToDto(),
                    tokenProfile.getTokenInstanceReference().getTokenInstanceUuid()
            );
        }
        logger.debug("Attributes for the new creation: {}", attributes);
        return attributes;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CRYPTOGRAPHIC_KEY, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.DETAIL)
    public void syncKeys(SecuredUUID tokenInstanceUuid) throws ConnectorException {
        TokenInstanceReference tokenInstanceReference = tokenInstanceService.getTokenInstanceEntity(
                tokenInstanceUuid
        );
        List<KeyDataResponseDto> keys = keyManagementApiClient.listKeys(
                tokenInstanceReference.getConnector().mapToDto(),
                tokenInstanceUuid.toString()
        );
        // Iterate and check if Key is already available
//        for (KeyDataResponseDto response : keys) {
//            if (!cryptographicKeyRepository.findByKeyReferenceUuidAndName(response.getUuid(), response.getName()).isPresent()) {
//                logger.debug("Requesting the details of the Key with UUID {} from connector", response.getUuid());
//                createKeyEntity(
//                        response.getName(),
//                        "",
//                        null
//                );
//            }
//        }
        logger.info("Sync Key Completed");
    }

    private CryptographicKey createKeyEntity(String name, String description, UUID tokenProfileUuid) {
        CryptographicKey key = new CryptographicKey();
        key.setName(name);
        key.setDescription(description);
        key.setTokenProfileUuid(tokenProfileUuid);
        logger.debug("Cryptographic Key: {}", key);
        cryptographicKeyRepository.save(key);
        return key;
    }

    private void createKeyContent(String referenceUuid, KeyData keyData, CryptographicKey cryptographicKey) {
        logger.info("Creating the Key Content for {}", cryptographicKey);
        CryptographicKeyContent content = new CryptographicKeyContent();
        content.setCryptographicKey(cryptographicKey);
        content.setType(keyData.getType());
        content.setCryptographicAlgorithm(keyData.getAlgorithm());
        content.setKeyData(content.getKeyData());
        content.setFormat(keyData.getFormat());
        content.setLength(keyData.getLength());
        content.setKeyReferenceUuid(UUID.fromString(referenceUuid));
        cryptographicKeyContentRepository.save(content);
    }

    private CryptographicKey getCryptographicKeyEntity(UUID uuid) throws NotFoundException {
        return cryptographicKeyRepository
                .findByUuid(uuid)
                .orElseThrow(
                        () -> new NotFoundException(
                                CryptographicKey.class,
                                uuid
                        )
                );
    }

    private List<DataAttribute> mergeAndValidateAttributes(KeyRequestType type, TokenInstanceReference tokenInstanceRef, List<RequestAttributeDto> attributes) throws ConnectorException {
        logger.debug("Merging and validating attributes on token profile {}. Request Attributes are: {}", tokenInstanceRef, attributes);
        List<BaseAttribute> definitions;
        if (type.equals(KeyRequestType.KEY_PAIR)) {
            definitions = keyManagementApiClient.listCreateKeyPairAttributes(
                    tokenInstanceRef.getConnector().mapToDto(),
                    tokenInstanceRef.getTokenInstanceUuid()
            );
        } else {
            definitions = keyManagementApiClient.listCreateSecretKeyAttributes(
                    tokenInstanceRef.getConnector().mapToDto(),
                    tokenInstanceRef.getTokenInstanceUuid()
            );
        }
        logger.debug("Attributes from connector: {}", definitions);
        List<String> existingAttributesFromConnector = definitions
                .stream()
                .map(BaseAttribute::getName)
                .collect(Collectors.toList());
        logger.debug("List of attributes from the connector: {}", existingAttributesFromConnector);
        for (RequestAttributeDto requestAttributeDto : attributes) {
            if (!existingAttributesFromConnector.contains(requestAttributeDto.getName())) {
                DataAttribute referencedAttribute = attributeService.getReferenceAttribute(
                        tokenInstanceRef.getConnectorUuid(),
                        requestAttributeDto.getName()
                );
                if (referencedAttribute != null) {
                    definitions.add(referencedAttribute);
                }
            }
        }

        List<DataAttribute> merged = AttributeDefinitionUtils.mergeAttributes(
                definitions,
                attributes
        );
        logger.debug("Merged attributes: {}", merged);

        if (type.equals(KeyRequestType.KEY_PAIR)) {
            keyManagementApiClient.validateCreateKeyPairAttributes(
                    tokenInstanceRef.getConnector().mapToDto(),
                    tokenInstanceRef.getTokenInstanceUuid(),
                    attributes
            );
        } else {
            keyManagementApiClient.validateCreateSecretKeyAttributes(
                    tokenInstanceRef.getConnector().mapToDto(),
                    tokenInstanceRef.getTokenInstanceUuid(),
                    attributes
            );
        }

        return merged;
    }
}
