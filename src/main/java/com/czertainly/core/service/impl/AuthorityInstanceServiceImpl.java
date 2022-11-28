package com.czertainly.core.service.impl;

import com.czertainly.api.clients.AuthorityInstanceApiClient;
import com.czertainly.api.clients.EndEntityProfileApiClient;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.authority.AuthorityInstanceUpdateRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.NameAndIdDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.connector.authority.AuthorityProviderInstanceDto;
import com.czertainly.api.model.connector.authority.AuthorityProviderInstanceRequestDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.authority.AuthorityInstanceDto;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.AuthorityInstanceReference;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.Connector2FunctionGroup;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.AuthorityInstanceReferenceRepository;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AttributeService;
import com.czertainly.core.service.AuthorityInstanceService;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.service.CredentialService;
import com.czertainly.core.service.RaProfileService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.SecretMaskingUtil;
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
public class AuthorityInstanceServiceImpl implements AuthorityInstanceService {
    private static final Logger logger = LoggerFactory.getLogger(AuthorityInstanceServiceImpl.class);

    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;
    @Autowired
    private ConnectorService connectorService;
    @Autowired
    private CredentialService credentialService;
    @Autowired
    private AuthorityInstanceApiClient authorityInstanceApiClient;
    @Autowired
    private EndEntityProfileApiClient endEntityProfileApiClient;
    @Autowired
    private RaProfileService raProfileService;
    @Autowired
    private AttributeService attributeService;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.LIST)
    public List<AuthorityInstanceDto> listAuthorityInstances(SecurityFilter filter) {
        return authorityInstanceReferenceRepository.findUsingSecurityFilter(filter)
                .stream()
                .map(AuthorityInstanceReference::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.DETAIL)
    public AuthorityInstanceDto getAuthorityInstance(SecuredUUID uuid) throws ConnectorException {
        AuthorityInstanceReference authorityInstanceReference = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));

        AuthorityInstanceDto authorityInstanceDto = new AuthorityInstanceDto();
        authorityInstanceDto.setName(authorityInstanceReference.getName());
        authorityInstanceDto.setUuid(authorityInstanceReference.getUuid().toString());
        authorityInstanceDto.setKind(authorityInstanceReference.getKind());
        if (authorityInstanceReference.getConnector() == null) {
            authorityInstanceDto.setConnectorName(authorityInstanceReference.getConnectorName() + " (Deleted)");
            authorityInstanceDto.setConnectorUuid("");
            logger.warn("Connector associated with the Authority: {} is not found. Unable to show details", authorityInstanceReference);
            return authorityInstanceDto;
        }

        AuthorityProviderInstanceDto authorityProviderInstanceDto = authorityInstanceApiClient.getAuthorityInstance(authorityInstanceReference.getConnector().mapToDto(),
                authorityInstanceReference.getAuthorityInstanceUuid());

        authorityInstanceDto.setAttributes(SecretMaskingUtil.maskSecret(AttributeDefinitionUtils.getResponseAttributes(authorityProviderInstanceDto.getAttributes())));
        authorityInstanceDto.setName(authorityProviderInstanceDto.getName());
        authorityInstanceDto.setConnectorName(authorityInstanceReference.getConnector().getName());
        authorityInstanceDto.setConnectorUuid(authorityInstanceReference.getConnector().getUuid().toString());
        authorityInstanceDto.setCustomAttributes(attributeService.getCustomAttributesWithValues(uuid.getValue(), Resource.AUTHORITY));
        return authorityInstanceDto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.CREATE)
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.CREATE)
    public AuthorityInstanceDto createAuthorityInstance(com.czertainly.api.model.client.authority.AuthorityInstanceRequestDto request) throws AlreadyExistException, ConnectorException {
        if (authorityInstanceReferenceRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(AuthorityInstanceReference.class, request.getName());
        }

        Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromString(request.getConnectorUuid()));

        FunctionGroupCode codeToSearch = FunctionGroupCode.AUTHORITY_PROVIDER;

        for (Connector2FunctionGroup function : connector.getFunctionGroups()) {
            if (function.getFunctionGroup().getCode() == FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER) {
                codeToSearch = FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER;
                break;
            }
        }
        attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.AUTHORITY);
        List<DataAttribute> attributes = connectorService.mergeAndValidateAttributes(connector.getSecuredUuid(), codeToSearch,
                request.getAttributes(), request.getKind());

        // Load complete credential data
        credentialService.loadFullCredentialData(attributes);

        AuthorityProviderInstanceRequestDto authorityInstanceDto = new AuthorityProviderInstanceRequestDto();
        authorityInstanceDto.setAttributes(AttributeDefinitionUtils.getClientAttributes(attributes));
        authorityInstanceDto.setKind(request.getKind());
        authorityInstanceDto.setName(request.getName());

        AuthorityProviderInstanceDto response = authorityInstanceApiClient.createAuthorityInstance(connector.mapToDto(), authorityInstanceDto);

        AuthorityInstanceReference authorityInstanceRef = new AuthorityInstanceReference();
        authorityInstanceRef.setAuthorityInstanceUuid(response.getUuid());
        authorityInstanceRef.setName(request.getName());
        authorityInstanceRef.setStatus("connected");
        authorityInstanceRef.setConnector(connector);
        authorityInstanceRef.setKind(request.getKind());
        authorityInstanceRef.setConnectorName(connector.getName());
        authorityInstanceReferenceRepository.save(authorityInstanceRef);

        attributeService.createAttributeContent(authorityInstanceRef.getUuid(), request.getCustomAttributes(), Resource.AUTHORITY);

        AuthorityInstanceDto dto = authorityInstanceRef.mapToDto();
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(authorityInstanceRef.getUuid(), Resource.AUTHORITY));
        return authorityInstanceRef.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.UPDATE)
    public AuthorityInstanceDto editAuthorityInstance(SecuredUUID uuid, AuthorityInstanceUpdateRequestDto request) throws ConnectorException {
        AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));
        AuthorityInstanceDto ref = getAuthorityInstance(uuid);
        Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromString(ref.getConnectorUuid()));

        FunctionGroupCode codeToSearch = FunctionGroupCode.AUTHORITY_PROVIDER;

        for (Connector2FunctionGroup function : connector.getFunctionGroups()) {
            if (function.getFunctionGroup().getCode() == FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER) {
                codeToSearch = FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER;
                break;
            }
        }
        attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.AUTHORITY);
        List<DataAttribute> attributes = connectorService.mergeAndValidateAttributes(connector.getSecuredUuid(), codeToSearch,
                request.getAttributes(), ref.getKind());

        // Load complete credential data
        credentialService.loadFullCredentialData(attributes);

        AuthorityProviderInstanceRequestDto authorityInstanceDto = new AuthorityProviderInstanceRequestDto();
        authorityInstanceDto.setKind(ref.getKind());
        authorityInstanceDto.setName(ref.getName());
        authorityInstanceDto.setAttributes(AttributeDefinitionUtils.getClientAttributes(attributes));
        authorityInstanceApiClient.updateAuthorityInstance(connector.mapToDto(),
                authorityInstanceRef.getAuthorityInstanceUuid(), authorityInstanceDto);
        authorityInstanceReferenceRepository.save(authorityInstanceRef);

        attributeService.updateAttributeContent(authorityInstanceRef.getUuid(), request.getCustomAttributes(), Resource.AUTHORITY);

        AuthorityInstanceDto dto = authorityInstanceRef.mapToDto();
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(authorityInstanceRef.getUuid(), Resource.AUTHORITY));

        return dto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.DELETE)
    public void deleteAuthorityInstance(SecuredUUID uuid) throws ConnectorException {
        AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));
        removeAuthorityInstance(authorityInstanceRef);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.END_ENTITY_PROFILE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.DETAIL)
    public List<NameAndIdDto> listEndEntityProfiles(SecuredUUID uuid) throws ConnectorException {
        AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));

        return endEntityProfileApiClient.listEndEntityProfiles(authorityInstanceRef.getConnector().mapToDto(),
                authorityInstanceRef.getAuthorityInstanceUuid());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.END_ENTITY_PROFILE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.DETAIL)
    public List<NameAndIdDto> listCertificateProfiles(SecuredUUID uuid, Integer endEntityProfileId) throws ConnectorException {
        AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));

        return endEntityProfileApiClient.listCertificateProfiles(authorityInstanceRef.getConnector().mapToDto(),
                authorityInstanceRef.getAuthorityInstanceUuid(), endEntityProfileId);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.END_ENTITY_PROFILE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.DETAIL)
    public List<NameAndIdDto> listCAsInProfile(SecuredUUID uuid, Integer endEntityProfileId) throws ConnectorException {
        AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));

        return endEntityProfileApiClient.listCAsInProfile(authorityInstanceRef.getConnector().mapToDto(),
                authorityInstanceRef.getAuthorityInstanceUuid(), endEntityProfileId);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.ANY)
    public List<BaseAttribute> listRAProfileAttributes(SecuredUUID uuid) throws ConnectorException {
        AuthorityInstanceReference authorityInstance = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));
        Connector connector = authorityInstance.getConnector();

        return authorityInstanceApiClient.listRAProfileAttributes(connector.mapToDto(), authorityInstance.getAuthorityInstanceUuid());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.VALIDATE)
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.ANY)
    public Boolean validateRAProfileAttributes(SecuredUUID uuid, List<RequestAttributeDto> attributes) throws ConnectorException {
        AuthorityInstanceReference authorityInstance = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));
        Connector connector = authorityInstance.getConnector();

        return authorityInstanceApiClient.validateRAProfileAttributes(connector.mapToDto(), authorityInstance.getAuthorityInstanceUuid(),
                attributes);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.DELETE)
    public List<BulkActionMessageDto> bulkDeleteAuthorityInstance(List<SecuredUUID> uuids) throws ValidationException {
        List<BulkActionMessageDto> messages = new ArrayList<>();
        for (SecuredUUID uuid : uuids) {
            AuthorityInstanceReference authorityInstanceRef = null;
            try {
                authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));
                removeAuthorityInstance(authorityInstanceRef);
            } catch (NotFoundException e) {
                logger.error("Authority Instance not found: {}", uuid);
            } catch (Exception e) {
                logger.warn(e.getMessage());
                messages.add(new BulkActionMessageDto(uuid.toString(), authorityInstanceRef != null ? authorityInstanceRef.getName() : "", e.getMessage()));
            }
        }
        return messages;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.FORCE_DELETE)
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.DELETE)
    public List<BulkActionMessageDto> forceDeleteAuthorityInstance(List<SecuredUUID> uuids) throws ValidationException {
        List<BulkActionMessageDto> messages = new ArrayList<>();
        for (SecuredUUID uuid : uuids) {
            AuthorityInstanceReference authorityInstanceRef = null;
            try {
                authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));
                if (!authorityInstanceRef.getRaProfiles().isEmpty()) {
                    for (RaProfile ref : authorityInstanceRef.getRaProfiles()) {
                        ref.setAuthorityInstanceReference(null);
                        raProfileService.updateRaProfileEntity(ref);
                    }
                }
                authorityInstanceRef.setRaProfiles(null);
                authorityInstanceReferenceRepository.save(authorityInstanceRef);
                removeAuthorityInstance(authorityInstanceRef);
            } catch (Exception e) {
                logger.warn("Unable to delete the Authority instance with uuid {}. It may have been deleted. {}", uuid, e.getMessage());
                messages.add(new BulkActionMessageDto(uuid.toString(), authorityInstanceRef != null ? authorityInstanceRef.getName() : "", e.getMessage()));
            }
        }
        return messages;
    }

    private void removeAuthorityInstance(AuthorityInstanceReference authorityInstanceRef) throws ValidationException {
        ValidationError error = null;
        if (authorityInstanceRef.getRaProfiles() != null && !authorityInstanceRef.getRaProfiles().isEmpty()) {
            error = ValidationError.create("Dependent RA profiles: {}", String.join(" ,", authorityInstanceRef.getRaProfiles().stream().map(RaProfile::getName).collect(Collectors.toSet())));
        }

        if (error != null) {
            throw new ValidationException(error);
        }
        if (authorityInstanceRef.getConnector() != null) {
            try {
                authorityInstanceApiClient.removeAuthorityInstance(authorityInstanceRef.getConnector().mapToDto(), authorityInstanceRef.getAuthorityInstanceUuid());
            } catch (Exception e) {
                logger.error(e.getMessage());
                throw new ValidationException(e.getMessage());
            }
        } else {
            logger.debug("Deleting authority without connector: {}", authorityInstanceRef);
        }
        attributeService.deleteAttributeContent(authorityInstanceRef.getUuid(), Resource.AUTHORITY);
        authorityInstanceReferenceRepository.delete(authorityInstanceRef);
    }
}
