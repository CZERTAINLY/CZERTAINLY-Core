package com.czertainly.core.service.impl;

import com.czertainly.api.clients.cryptography.TokenInstanceApiClient;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.cryptography.token.TokenInstanceRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.connector.cryptography.enums.TokenInstanceStatus;
import com.czertainly.api.model.connector.cryptography.token.TokenInstanceStatusDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceDetailDto;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceDto;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceStatusDetailDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.TokenInstanceReference;
import com.czertainly.core.dao.entity.TokenProfile;
import com.czertainly.core.dao.repository.TokenInstanceReferenceRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AttributeService;
import com.czertainly.core.service.CredentialService;
import com.czertainly.core.service.MetadataService;
import com.czertainly.core.service.TokenInstanceService;
import com.czertainly.core.util.AttributeDefinitionUtils;

import org.hibernate.annotations.common.util.impl.Log_.logger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class TokenInstanceServiceImpl implements TokenInstanceService {

    private static final Logger logger = LoggerFactory.getLogger(TokenInstanceServiceImpl.class);

    // --------------------------------------------------------------------------------
    // Services & API Clients
    // --------------------------------------------------------------------------------
    private AttributeService attributeService;
    private TokenInstanceApiClient tokenInstanceApiClient;
    private MetadataService metadataService;
    private ConnectorServiceImpl connectorService;
    private CredentialService credentialService;
    // --------------------------------------------------------------------------------
    // Repositories
    // --------------------------------------------------------------------------------
    private TokenInstanceReferenceRepository tokenInstanceReferenceRepository;

    @Autowired
    public void setAttributeService(AttributeService attributeService) {
        this.attributeService = attributeService;
    }

    @Autowired
    public void setTokenInstanceApiClient(TokenInstanceApiClient tokenInstanceApiClient) {
        this.tokenInstanceApiClient = tokenInstanceApiClient;
    }

    @Autowired
    public void setTokenInstanceReferenceRepository(TokenInstanceReferenceRepository tokenInstanceReferenceRepository) {
        this.tokenInstanceReferenceRepository = tokenInstanceReferenceRepository;
    }

    @Autowired
    public void setMetadataService(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Autowired
    public void setConnectorService(ConnectorServiceImpl connectorService) {
        this.connectorService = connectorService;
    }

    @Autowired
    public void setCredentialService(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    //-------------------------------------------------------------------------------------
    //Service Implementations
    //-------------------------------------------------------------------------------------

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_INSTANCE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.TOKEN_INSTANCE, action = ResourceAction.LIST)
    public List<TokenInstanceDto> listTokenInstances(SecurityFilter filter) {
        logger.info("Listing token instances");
        return tokenInstanceReferenceRepository.findUsingSecurityFilter(filter)
                .stream()
                .map(TokenInstanceReference::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_INSTANCE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.TOKEN_INSTANCE, action = ResourceAction.DETAIL)
    public TokenInstanceDetailDto getTokenInstance(SecuredUUID uuid) throws ConnectorException {
        logger.info("Getting token instance with uuid: {}", uuid);
        TokenInstanceReference tokenInstanceReference = getTokenInstanceReferenceEntity(uuid);
        TokenInstanceDetailDto tokenInstanceDetailDto = tokenInstanceReference.mapToDetailDto();
        logger.debug("Token Instance detail: {}", tokenInstanceDetailDto);
        if (tokenInstanceReference.getConnector() == null) {
            tokenInstanceDetailDto.setConnectorName(tokenInstanceReference.getConnectorName() + " (Deleted)");
            tokenInstanceDetailDto.setConnectorUuid("");
            logger.warn("Connector associated with the Authority: {} is not found. Unable to show details", tokenInstanceReference);
            return tokenInstanceDetailDto;
        }

        TokenInstanceStatusDto status;
        TokenInstanceStatusDetailDto statusDetail = new TokenInstanceStatusDetailDto();
        try {
            status = tokenInstanceApiClient.getTokenInstanceStatus(
                    tokenInstanceReference.getConnector().mapToDto(),
                    tokenInstanceReference.getTokenInstanceUuid()
            );
            tokenInstanceReference.setStatus(status.getStatus());
            tokenInstanceReferenceRepository.save(tokenInstanceReference);
            statusDetail.setStatus(status.getStatus());
            statusDetail.setComponents(status.getComponents());
        } catch (ConnectorException e) {
            logger.error("Unable to communicate with connector", e.getMessage());
            statusDetail.setStatus(TokenInstanceStatus.UNKNOWN);
            tokenInstanceDetailDto.setStatus(statusDetail);
        }

        tokenInstanceDetailDto.setStatus(statusDetail);
        tokenInstanceDetailDto.setConnectorName(tokenInstanceReference.getConnector().getName());
        tokenInstanceDetailDto.setConnectorUuid(tokenInstanceReference.getConnector().getUuid().toString());
        tokenInstanceDetailDto.setCustomAttributes(attributeService.getCustomAttributesWithValues(uuid.getValue(), Resource.TOKEN_INSTANCE));
        tokenInstanceDetailDto.setMetadata(metadataService.getFullMetadata(tokenInstanceReference.getUuid(), Resource.TOKEN_INSTANCE));
        logger.debug("Token Instance detail: {}", tokenInstanceDetailDto);
        return tokenInstanceDetailDto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_INSTANCE, operation = OperationType.CREATE)
    @ExternalAuthorization(resource = Resource.TOKEN_INSTANCE, action = ResourceAction.CREATE)
    public TokenInstanceDetailDto createTokenInstance(TokenInstanceRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException {
        logger.info("Creating token instance with name: {}", request.getName());
        if (tokenInstanceReferenceRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(TokenInstanceReference.class, request.getName());
        }

        Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromString(request.getConnectorUuid()));
        logger.debug("Connector: {}", connector);

        attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.TOKEN_INSTANCE);
        List<DataAttribute> attributes = connectorService.mergeAndValidateAttributes(connector.getSecuredUuid(),
                FunctionGroupCode.CRYPTOGRAPHY_PROVIDER,
                request.getAttributes(),
                request.getKind());
        logger.debug("Merged and Validated Attributes: {}", attributes);
        // Load complete credential data
        credentialService.loadFullCredentialData(attributes);

        com.czertainly.api.model.connector.cryptography.token.TokenInstanceRequestDto tokenInstanceRequestDto =
                new com.czertainly.api.model.connector.cryptography.token.TokenInstanceRequestDto();
        tokenInstanceRequestDto.setAttributes(AttributeDefinitionUtils.getClientAttributes(attributes));
        tokenInstanceRequestDto.setKind(request.getKind());
        tokenInstanceRequestDto.setName(request.getName());
        logger.debug("Token Instance Request to the connector: {}", tokenInstanceRequestDto);
        com.czertainly.api.model.connector.cryptography.token.TokenInstanceDto response =
                tokenInstanceApiClient.createTokenInstance(connector.mapToDto(), tokenInstanceRequestDto);
        TokenInstanceStatusDto status = tokenInstanceApiClient.getTokenInstanceStatus(connector.mapToDto(), response.getUuid());
        logger.debug("Token Instance Response from the connector: {}", response);
        TokenInstanceReference tokenInstanceReference = new TokenInstanceReference();
        tokenInstanceReference.setTokenInstanceUuid(response.getUuid());
        tokenInstanceReference.setName(request.getName());
        tokenInstanceReference.setConnector(connector);
        tokenInstanceReference.setKind(request.getKind());
        tokenInstanceReference.setConnectorName(connector.getName());
        tokenInstanceReference.setStatus(status.getStatus());
        tokenInstanceReference.setAttributes(attributes);
        logger.debug("Token Instance Reference: {}", tokenInstanceReference);
        tokenInstanceReferenceRepository.save(tokenInstanceReference);

        attributeService.createAttributeContent(tokenInstanceReference.getUuid(), request.getCustomAttributes(), Resource.TOKEN_INSTANCE);
        metadataService.createMetadataDefinitions(connector.getUuid(), response.getMetadata());
        metadataService.createMetadata(connector.getUuid(), tokenInstanceReference.getUuid(), null, null, response.getMetadata(), Resource.TOKEN_INSTANCE, null);
        logger.debug("Metadata and Custom attributes created");
        TokenInstanceDetailDto dto = tokenInstanceReference.mapToDetailDto();
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(tokenInstanceReference.getUuid(), Resource.TOKEN_INSTANCE));
        dto.setMetadata(metadataService.getFullMetadata(tokenInstanceReference.getUuid(), Resource.TOKEN_INSTANCE));
        logger.debug("Token Instance detail: {}", dto);
        return dto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_INSTANCE, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.TOKEN_INSTANCE, action = ResourceAction.UPDATE)
    public TokenInstanceDetailDto updateTokenInstance(SecuredUUID uuid, TokenInstanceRequestDto request) throws ConnectorException, ValidationException {
        logger.info("Updating token instance with uuid: {}", uuid);
        TokenInstanceReference tokenInstanceReference = getTokenInstanceReferenceEntity(uuid);
        TokenInstanceDetailDto ref = getTokenInstance(uuid);
        logger.debug("Token Instance Reference: {}", tokenInstanceReference);
        Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromString(ref.getConnectorUuid()));

        attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.TOKEN_INSTANCE);
        List<DataAttribute> attributes = connectorService.mergeAndValidateAttributes(connector.getSecuredUuid(),
                FunctionGroupCode.CRYPTOGRAPHY_PROVIDER,
                request.getAttributes(),
                request.getKind());
        logger.debug("Merged and Validated Attributes: {}", attributes);

        // Load complete credential data
        credentialService.loadFullCredentialData(attributes);

        com.czertainly.api.model.connector.cryptography.token.TokenInstanceRequestDto tokenInstanceRequestDto =
                new com.czertainly.api.model.connector.cryptography.token.TokenInstanceRequestDto();
        tokenInstanceRequestDto.setAttributes(AttributeDefinitionUtils.getClientAttributes(attributes));
        tokenInstanceRequestDto.setKind(request.getKind());
        tokenInstanceRequestDto.setName(request.getName());
        logger.debug("Token Instance Request to the connector: {}", tokenInstanceRequestDto);
        com.czertainly.api.model.connector.cryptography.token.TokenInstanceDto response =
                tokenInstanceApiClient.updateTokenInstance(connector.mapToDto(), tokenInstanceReference.getTokenInstanceUuid(), tokenInstanceRequestDto);

        attributeService.updateAttributeContent(tokenInstanceReference.getUuid(), request.getCustomAttributes(), Resource.TOKEN_INSTANCE);
        metadataService.createMetadataDefinitions(connector.getUuid(), response.getMetadata());
        metadataService.createMetadata(connector.getUuid(), tokenInstanceReference.getUuid(), null, null, response.getMetadata(), Resource.TOKEN_INSTANCE, null);
        logger.debug("Metadata and Custom attributes updated");
        TokenInstanceDetailDto dto = tokenInstanceReference.mapToDetailDto();
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(tokenInstanceReference.getUuid(), Resource.TOKEN_INSTANCE));
        dto.setMetadata(metadataService.getFullMetadata(tokenInstanceReference.getUuid(), Resource.TOKEN_INSTANCE));
        logger.debug("Token Instance detail: {}", dto);
        return dto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_INSTANCE, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.TOKEN_INSTANCE, action = ResourceAction.DELETE)
    public void deleteTokenInstance(SecuredUUID uuid) throws NotFoundException {
        logger.info("Deleting token instance with uuid: {}", uuid);
        TokenInstanceReference tokenInstanceReference = getTokenInstanceReferenceEntity(uuid);
        removeTokenInstance(tokenInstanceReference);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_INSTANCE, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.TOKEN_INSTANCE, action = ResourceAction.ACTIVATE)
    public void activateTokenInstance(SecuredUUID uuid, List<RequestAttributeDto> attributes) throws ConnectorException {
        logger.info("Activating token instance with uuid: {}", uuid);
        TokenInstanceReference tokenInstanceReference = getTokenInstanceReferenceEntity(uuid);
        tokenInstanceApiClient.activateTokenInstance(
                tokenInstanceReference.getConnector().mapToDto(),
                tokenInstanceReference.getTokenInstanceUuid(),
                attributes
        );
        logger.info("Token instance activated");
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_INSTANCE, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.TOKEN_INSTANCE, action = ResourceAction.ACTIVATE)
    public void deactivateTokenInstance(SecuredUUID uuid) throws ConnectorException {
        logger.info("Deactivating token instance with uuid: {}", uuid);
        TokenInstanceReference tokenInstanceReference = getTokenInstanceReferenceEntity(uuid);
        tokenInstanceApiClient.deactivateTokenInstance(
                tokenInstanceReference.getConnector().mapToDto(),
                tokenInstanceReference.getTokenInstanceUuid()
        );
        logger.info("Token instance deactivated");
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_INSTANCE, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.TOKEN_INSTANCE, action = ResourceAction.DELETE)
    //TODO - Force delete?
    public void deleteTokenInstance(List<SecuredUUID> uuids) {
        logger.info("Deleting token instances with uuids: {}", uuids);
        List<BulkActionMessageDto> messages = new ArrayList<>();
        for (SecuredUUID uuid : uuids) {
            TokenInstanceReference tokenInstanceReference = null;
            try {
                tokenInstanceReference = getTokenInstanceReferenceEntity(uuid);
                removeTokenInstance(tokenInstanceReference);
            } catch (NotFoundException e) {
                logger.error("Token Instance not found: {}", uuid);
            } catch (Exception e) {
                logger.warn(e.getMessage());
                messages.add(new BulkActionMessageDto(uuid.toString(), tokenInstanceReference != null ? tokenInstanceReference.getName() : "", e.getMessage()));
            }
        }
//        return messages;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_INSTANCE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.TOKEN_INSTANCE, action = ResourceAction.DETAIL)
    public TokenInstanceDetailDto reloadStatus(SecuredUUID uuid) throws ConnectorException {
        logger.info("Reloading status of token instance with uuid: {}", uuid);
        TokenInstanceReference tokenInstanceReference = getTokenInstanceReferenceEntity(uuid);
        TokenInstanceStatusDto status = tokenInstanceApiClient.getTokenInstanceStatus(
                tokenInstanceReference.getConnector().mapToDto(),
                tokenInstanceReference.getTokenInstanceUuid()
        );
        tokenInstanceReference.setStatus(status.getStatus());
        tokenInstanceReferenceRepository.save(tokenInstanceReference);
        logger.info("Token instance status reloaded. Status of the token instance: {}", status);
        //TODO - Token Instance Status Components
        return getTokenInstance(uuid);

    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_INSTANCE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.TOKEN_INSTANCE, action = ResourceAction.DETAIL)
    public List<BaseAttribute> listTokenProfileAttributes(SecuredUUID uuid) throws ConnectorException {
        logger.info("Listing token profile attributes of token instance with uuid: {}", uuid);
        TokenInstanceReference tokenInstanceReference = getTokenInstanceReferenceEntity(uuid);
        logger.debug("Token instance detail: {}", tokenInstanceReference);
        return tokenInstanceApiClient.listTokenProfileAttributes(
                tokenInstanceReference.getConnector().mapToDto(),
                tokenInstanceReference.getTokenInstanceUuid()
        );
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.VALIDATE)
    @ExternalAuthorization(resource = Resource.TOKEN_INSTANCE, action = ResourceAction.ANY)
    public void validateTokenProfileAttributes(SecuredUUID uuid, List<RequestAttributeDto> attributes) throws ConnectorException {
        logger.info("Validating token profile attributes of token instance with uuid: {}", uuid);
        TokenInstanceReference tokenInstanceReference = getTokenInstanceReferenceEntity(uuid);
        logger.debug("Token instance detail: {}", tokenInstanceReference);
        Connector connector = tokenInstanceReference.getConnector();
        tokenInstanceApiClient.validateTokenProfileAttributes(connector.mapToDto(), tokenInstanceReference.getTokenInstanceUuid(),
                attributes);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_INSTANCE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.TOKEN_INSTANCE, action = ResourceAction.DETAIL)
    public List<BaseAttribute> listTokenInstanceActivationAttributes(SecuredUUID uuid) throws ConnectorException {
        logger.info("Listing token instance activation attributes of token instance with uuid: {}", uuid);
        TokenInstanceReference tokenInstanceReference = getTokenInstanceReferenceEntity(uuid);
        logger.debug("Token instance detail: {}", tokenInstanceReference);
        return tokenInstanceApiClient.listTokenInstanceActivationAttributes(
                tokenInstanceReference.getConnector().mapToDto(),
                tokenInstanceReference.getTokenInstanceUuid()
        );
    }

    @Override
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.LIST)
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter) {
        logger.info("Listing resource objects with filter: {}", filter);
        return tokenInstanceReferenceRepository.findUsingSecurityFilter(filter)
                .stream()
                .map(TokenInstanceReference::mapToAccessControlObjects)
                .collect(Collectors.toList());
    }

    private TokenInstanceReference getTokenInstanceReferenceEntity(SecuredUUID uuid) throws NotFoundException {
        return tokenInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(TokenInstanceReference.class, uuid));
    }

    private void removeTokenInstance(TokenInstanceReference tokenInstanceReference) throws ValidationException {
        logger.info("Removing token instance: {}", tokenInstanceReference);
        ValidationError error = null;
        if (tokenInstanceReference.getTokenProfiles() != null && !tokenInstanceReference.getTokenProfiles().isEmpty()) {
            error = ValidationError.create("Dependent Token Profiles: {}",
                    String.join(" ,",
                            tokenInstanceReference
                                    .getTokenProfiles()
                                    .stream()
                                    .map(TokenProfile::getName)
                                    .collect(Collectors.toSet()
                                    )
                    )
            );
        }

        if (error != null) {
            logger.error("Token Instances has associations and cannot be deleted: {}", error);
            throw new ValidationException(error);
        }
        if (tokenInstanceReference.getConnector() != null) {
            try {
                logger.debug("Deleting token instance with connector: {}", tokenInstanceReference);
                tokenInstanceApiClient.removeTokenInstance(tokenInstanceReference.getConnector().mapToDto(), tokenInstanceReference.getTokenInstanceUuid());
            } catch (Exception e) {
                logger.error(e.getMessage());
                throw new ValidationException(e.getMessage());
            }
        } else {
            logger.debug("Deleting token instance without connector: {}", tokenInstanceReference);
        }
        logger.debug("Deleting token instance attributes");
        attributeService.deleteAttributeContent(tokenInstanceReference.getUuid(), Resource.TOKEN_INSTANCE);
        tokenInstanceReferenceRepository.delete(tokenInstanceReference);
        logger.info("Token instance removed: {}", tokenInstanceReference);
    }
}
