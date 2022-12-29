package com.czertainly.core.service.impl;

import com.czertainly.api.clients.cryptography.KeyManagementApiClient;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.cryptography.key.KeyRequestDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.connector.cryptography.key.CreateKeyRequestDto;
import com.czertainly.api.model.connector.cryptography.key.DestroyKeyRequestDto;
import com.czertainly.api.model.connector.cryptography.key.KeyDataResponseDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cryptography.key.KeyDetailDto;
import com.czertainly.api.model.core.cryptography.key.KeyDto;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceDetailDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.TokenInstanceReference;
import com.czertainly.core.dao.repository.CryptographicKeyRepository;
import com.czertainly.core.dao.repository.TokenInstanceReferenceRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AttributeService;
import com.czertainly.core.service.ConnectorService;
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
    private TokenInstanceService tokenInstanceService;
    private ConnectorService connectorService;
    private KeyManagementApiClient keyManagementApiClient;
    // --------------------------------------------------------------------------------
    // Repositories
    // --------------------------------------------------------------------------------
    private CryptographicKeyRepository cryptographicKeyRepository;
    private TokenInstanceReferenceRepository tokenInstanceReferenceRepository;

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
    public void setConnectorService(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @Autowired
    public void setCryptographicKeyRepository(CryptographicKeyRepository cryptographicKeyRepository) {
        this.cryptographicKeyRepository = cryptographicKeyRepository;
    }

    // ----------------------------------------------------------------------------------------------
    // Service Implementations
    // ----------------------------------------------------------------------------------------------

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CRYPTOGRAPHIC_KEY, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.LIST, parentResource = Resource.TOKEN_PROFILE, parentAction = ResourceAction.LIST)
    public List<KeyDto> listKeys(Optional<String> tokenInstanceUuid, SecurityFilter filter) {
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
    public KeyDetailDto getKey(SecuredParentUUID tokenInstanceUuid, String uuid) throws NotFoundException {
        CryptographicKey key = getCryptographicKeyEntity(UUID.fromString(uuid));
        KeyDetailDto dto = key.mapToDetailDto();
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(key.getUuid(), Resource.CRYPTOGRAPHIC_KEY));
        dto.setKeyAttributes(metadataService.getMetadata(key.getTokenInstanceReferenceUuid(), key.getUuid(), Resource.CRYPTOGRAPHIC_KEY));
        return dto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CRYPTOGRAPHIC_KEY, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.CREATE)
    public KeyDetailDto createKey(SecuredParentUUID tokenInstanceUuid, KeyRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException {
        if (cryptographicKeyRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException("Existing Key with same already exists");
        }
        if (request.getName() == null) {
            throw new ValidationException("Name is required for creating a new Key");
        }
        TokenInstanceReference tokenInstanceReference = tokenInstanceReferenceRepository.findByUuid(tokenInstanceUuid.getValue()).orElseThrow(() -> new NotFoundException(TokenInstanceReference.class, tokenInstanceUuid));
        TokenInstanceDetailDto dto = tokenInstanceReference.mapToDetailDto();
        Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromString(dto.getConnectorUuid()));

        List<DataAttribute> attributes = mergeAndValidateAttributes(tokenInstanceReference, request.getCreateKeyAttributes());
        CreateKeyRequestDto createKeyRequestDto = new CreateKeyRequestDto();
        createKeyRequestDto.setCreateKeyAttributes(request.getCreateKeyAttributes());
        createKeyRequestDto.setTokenProfileAttributes(AttributeDefinitionUtils.getClientAttributes(dto.getAttributes()));
        KeyDataResponseDto response = keyManagementApiClient.createKey(connector.mapToDto(), dto.getUuid(), createKeyRequestDto);

        CryptographicKey key = new CryptographicKey();
        key.setName(request.getName());
        key.setDescription(request.getDescription());
        key.setCryptographicAlgorithm(response.getCryptographicAlgorithm());
        key.setTokenInstanceReferenceUuid(UUID.fromString(dto.getUuid()));
        cryptographicKeyRepository.save(key);

        attributeService.createAttributeContent(key.getUuid(), request.getCustomAttributes(), Resource.CRYPTOGRAPHIC_KEY);
        metadataService.createMetadataDefinitions(connector.getUuid(), response.getKeyAttributes());
        metadataService.createMetadata(
                connector.getUuid(),
                key.getUuid(),
                null,
                null,
                response.getKeyAttributes(),
                Resource.CRYPTOGRAPHIC_KEY,
                null
        );

        KeyDetailDto keyDetailDto = key.mapToDetailDto();
        keyDetailDto.setCustomAttributes(attributeService.getCustomAttributesWithValues(key.getUuid(), Resource.CRYPTOGRAPHIC_KEY));
        keyDetailDto.setKeyAttributes(response.getKeyAttributes());
        keyDetailDto.setCustomAttributes(attributeService.getCustomAttributesWithValues(key.getUuid(), Resource.CRYPTOGRAPHIC_KEY));
        return keyDetailDto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CRYPTOGRAPHIC_KEY, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.DELETE)
    public void destroyKey(SecuredParentUUID tokenInstanceUuid, String uuid) throws ConnectorException {
        CryptographicKey key = getCryptographicKeyEntity(UUID.fromString(uuid));
        DestroyKeyRequestDto request = new DestroyKeyRequestDto();
        request.setKeyAttributes(metadataService.getMetadata(key.getTokenInstanceReferenceUuid(), key.getUuid(), Resource.CRYPTOGRAPHIC_KEY));
        keyManagementApiClient.destroyKey(
                key.getTokenInstanceReference().getConnector().mapToDto(),
                key.getTokenInstanceReferenceUuid().toString(),
                request
        );
        attributeService.deleteAttributeContent(key.getUuid(), Resource.CRYPTOGRAPHIC_KEY);
        cryptographicKeyRepository.delete(key);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CRYPTOGRAPHIC_KEY, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CRYPTOGRAPHIC_KEY, action = ResourceAction.CREATE)
    public List<BaseAttribute> listCreateKeyAttributes(SecuredUUID tokenInstanceUuid) throws ConnectorException {
        TokenInstanceDetailDto dto = tokenInstanceService.getTokenInstance(tokenInstanceUuid);
        Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromString(dto.getConnectorUuid()));
        return keyManagementApiClient.listCreateKeyAttributes(connector.mapToDto(), dto.getUuid());
    }

    private CryptographicKey getCryptographicKeyEntity(UUID uuid) throws NotFoundException {
        return cryptographicKeyRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(CryptographicKey.class, uuid));
    }

    private List<DataAttribute> mergeAndValidateAttributes(TokenInstanceReference tokenInstanceRef, List<RequestAttributeDto> attributes) throws ConnectorException {
        List<BaseAttribute> definitions = keyManagementApiClient.listCreateKeyAttributes(
                tokenInstanceRef.getConnector().mapToDto(),
                tokenInstanceRef.getTokenInstanceUuid());

        List<String> existingAttributesFromConnector = definitions.stream().map(BaseAttribute::getName).collect(Collectors.toList());
        for (RequestAttributeDto requestAttributeDto : attributes) {
            if (!existingAttributesFromConnector.contains(requestAttributeDto.getName())) {
                DataAttribute referencedAttribute = attributeService.getReferenceAttribute(tokenInstanceRef.getConnectorUuid(), requestAttributeDto.getName());
                if (referencedAttribute != null) {
                    definitions.add(referencedAttribute);
                }
            }
        }

        List<DataAttribute> merged = AttributeDefinitionUtils.mergeAttributes(definitions, attributes);

        keyManagementApiClient.validateCreateKeyAttributes(
                tokenInstanceRef.getConnector().mapToDto(),
                tokenInstanceRef.getTokenInstanceUuid(),
                attributes);

        return merged;
    }
}
