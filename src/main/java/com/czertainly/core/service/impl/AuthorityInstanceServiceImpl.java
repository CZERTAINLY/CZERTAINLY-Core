package com.czertainly.core.service.impl;

import com.czertainly.api.clients.AuthorityInstanceApiClient;
import com.czertainly.api.clients.EndEntityProfileApiClient;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.authority.AuthorityInstanceUpdateRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.NameAndIdDto;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.common.attribute.RequestAttributeDto;
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
import com.czertainly.core.service.AuthorityInstanceService;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.service.CredentialService;
import com.czertainly.core.service.RaProfileService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Secured({"ROLE_ADMINISTRATOR", "ROLE_SUPERADMINISTRATOR"})
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

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.REQUEST)
    public List<AuthorityInstanceDto> listAuthorityInstances() {
        return authorityInstanceReferenceRepository.findAll().stream().map(AuthorityInstanceReference::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.REQUEST)
    public AuthorityInstanceDto getAuthorityInstance(String uuid) throws ConnectorException {
        AuthorityInstanceReference authorityInstanceReference = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));

        AuthorityInstanceDto authorityInstanceDto = new AuthorityInstanceDto();
        authorityInstanceDto.setName(authorityInstanceReference.getName());
        authorityInstanceDto.setUuid(authorityInstanceReference.getUuid());
        authorityInstanceDto.setKind(authorityInstanceReference.getKind());
        if (authorityInstanceReference.getConnector() == null) {
            authorityInstanceDto.setConnectorName(authorityInstanceReference.getConnectorName() + " (Deleted)");
            authorityInstanceDto.setConnectorUuid("");
            logger.warn("Connector associated with the Authority: {} is not found. Unable to show details", authorityInstanceReference);
            return authorityInstanceDto;
        }

        AuthorityProviderInstanceDto authorityProviderInstanceDto = authorityInstanceApiClient.getAuthorityInstance(authorityInstanceReference.getConnector().mapToDto(),
                authorityInstanceReference.getAuthorityInstanceUuid());

        authorityInstanceDto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(authorityProviderInstanceDto.getAttributes()));
        authorityInstanceDto.setName(authorityProviderInstanceDto.getName());
        authorityInstanceDto.setConnectorName(authorityInstanceReference.getConnector().getName());
        authorityInstanceDto.setConnectorUuid(authorityInstanceReference.getConnector().getUuid());
        return authorityInstanceDto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.CREATE)
    public AuthorityInstanceDto createAuthorityInstance(com.czertainly.api.model.client.authority.AuthorityInstanceRequestDto request) throws AlreadyExistException, ConnectorException {
        if (authorityInstanceReferenceRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(AuthorityInstanceReference.class, request.getName());
        }

        Connector connector = connectorService.getConnectorEntity(request.getConnectorUuid());

        FunctionGroupCode codeToSearch = FunctionGroupCode.AUTHORITY_PROVIDER;

        for (Connector2FunctionGroup function : connector.getFunctionGroups()) {
            if (function.getFunctionGroup().getCode() == FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER) {
                codeToSearch = FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER;
                break;
            }
        }

        List<AttributeDefinition> attributes = connectorService.mergeAndValidateAttributes(connector.getUuid(), codeToSearch,
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

        return authorityInstanceRef.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.CHANGE)
    public AuthorityInstanceDto editAuthorityInstance(String uuid, AuthorityInstanceUpdateRequestDto request) throws ConnectorException {
        AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));
        AuthorityInstanceDto ref = getAuthorityInstance(uuid);
        Connector connector = connectorService.getConnectorEntity(ref.getConnectorUuid());

        FunctionGroupCode codeToSearch = FunctionGroupCode.AUTHORITY_PROVIDER;

        for (Connector2FunctionGroup function : connector.getFunctionGroups()) {
            if (function.getFunctionGroup().getCode() == FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER) {
                codeToSearch = FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER;
                break;
            }
        }

        List<AttributeDefinition> attributes = connectorService.mergeAndValidateAttributes(connector.getUuid(), codeToSearch,
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
        return authorityInstanceRef.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.DELETE)
    public void deleteAuthorityInstance(String uuid) throws ConnectorException {
        AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));
        removeAuthorityInstance(authorityInstanceRef);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.END_ENTITY_PROFILE, operation = OperationType.REQUEST)
    public List<NameAndIdDto> listEndEntityProfiles(String uuid) throws ConnectorException {
        AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));

        return endEntityProfileApiClient.listEndEntityProfiles(authorityInstanceRef.getConnector().mapToDto(),
                authorityInstanceRef.getAuthorityInstanceUuid());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.END_ENTITY_PROFILE, operation = OperationType.REQUEST)
    public List<NameAndIdDto> listCertificateProfiles(String uuid, Integer endEntityProfileId) throws ConnectorException {
        AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));

        return endEntityProfileApiClient.listCertificateProfiles(authorityInstanceRef.getConnector().mapToDto(),
                authorityInstanceRef.getAuthorityInstanceUuid(), endEntityProfileId);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.END_ENTITY_PROFILE, operation = OperationType.REQUEST)
    public List<NameAndIdDto> listCAsInProfile(String uuid, Integer endEntityProfileId) throws ConnectorException {
        AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));

        return endEntityProfileApiClient.listCAsInProfile(authorityInstanceRef.getConnector().mapToDto(),
                authorityInstanceRef.getAuthorityInstanceUuid(), endEntityProfileId);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    public List<AttributeDefinition> listRAProfileAttributes(String uuid) throws ConnectorException {
        AuthorityInstanceReference authorityInstance = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));
        Connector connector = authorityInstance.getConnector();

        return authorityInstanceApiClient.listRAProfileAttributes(connector.mapToDto(), authorityInstance.getAuthorityInstanceUuid());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.VALIDATE)
    public Boolean validateRAProfileAttributes(String uuid, List<RequestAttributeDto> attributes) throws ConnectorException {
        AuthorityInstanceReference authorityInstance = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));
        Connector connector = authorityInstance.getConnector();

        return authorityInstanceApiClient.validateRAProfileAttributes(connector.mapToDto(), authorityInstance.getAuthorityInstanceUuid(),
                attributes);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.DELETE)
    public List<BulkActionMessageDto> bulkDeleteAuthorityInstance(List<String> uuids) throws ValidationException {
        List<BulkActionMessageDto> messages = new ArrayList<>();
        for (String uuid : uuids) {
            AuthorityInstanceReference authorityInstanceRef = null;
            try {
                authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));
                removeAuthorityInstance(authorityInstanceRef);
            } catch (NotFoundException e) {
                logger.error("Authority Instance not found: {}", uuid);
            } catch (Exception e) {
                logger.warn(e.getMessage());
                messages.add(new BulkActionMessageDto(uuid, authorityInstanceRef != null ? authorityInstanceRef.getName() : "", e.getMessage()));
            }
        }
        return messages;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.FORCE_DELETE)
    public List<BulkActionMessageDto> forceDeleteAuthorityInstance(List<String> uuids) throws ValidationException {
        List<BulkActionMessageDto> messages = new ArrayList<>();
        for (String uuid : uuids) {
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
                messages.add(new BulkActionMessageDto(uuid, authorityInstanceRef != null ? authorityInstanceRef.getName() : "", e.getMessage()));
            }
        }
        return messages;
    }

    private void removeAuthorityInstance(AuthorityInstanceReference authorityInstanceRef) throws ValidationException {
        if (authorityInstanceRef.getConnector() != null) {
            ValidationError error = null;
            if (authorityInstanceRef.getRaProfiles() != null && !authorityInstanceRef.getRaProfiles().isEmpty()) {
                error = ValidationError.create("Dependent RA profiles: {}", String.join(" ,", authorityInstanceRef.getRaProfiles().stream().map(RaProfile::getName).collect(Collectors.toSet())));
            }

            if (error != null) {
                throw new ValidationException(error);
            }
            try {
                authorityInstanceApiClient.removeAuthorityInstance(authorityInstanceRef.getConnector().mapToDto(), authorityInstanceRef.getAuthorityInstanceUuid());
            } catch (Exception e) {
                logger.error(e.getMessage());
                throw new ValidationException(e.getMessage());
            }
        } else {
            logger.debug("Deleting authority without connector: {}", authorityInstanceRef);
        }
        authorityInstanceReferenceRepository.delete(authorityInstanceRef);
    }
}
