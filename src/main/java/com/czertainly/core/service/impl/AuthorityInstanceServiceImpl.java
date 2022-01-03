package com.czertainly.core.service.impl;

import com.czertainly.api.clients.AttributeApiClient;
import com.czertainly.api.clients.AuthorityInstanceApiClient;
import com.czertainly.api.clients.EndEntityProfileApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.authority.AuthorityInstanceUpdateRequestDto;
import com.czertainly.api.model.client.connector.ForceDeleteMessageDto;
import com.czertainly.api.model.common.AttributeDefinition;
import com.czertainly.api.model.common.NameAndIdDto;
import com.czertainly.api.model.common.RequestAttributeDto;
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
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.service.AuthorityInstanceService;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.service.CoreCallbackService;
import com.czertainly.core.service.CredentialService;
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
    private AttributeApiClient attributeApiClient;
    @Autowired
    private CoreCallbackService coreCallbackService;
    @Autowired
    private RaProfileRepository raProfileRepository;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.REQUEST)
    public List<AuthorityInstanceDto> listAuthorityInstances() {
        return authorityInstanceReferenceRepository.findAll().stream().map(AuthorityInstanceReference::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.REQUEST)
    public AuthorityInstanceDto getAuthorityInstance(String uuid) throws NotFoundException, ConnectorException {
        AuthorityInstanceReference authorityInstanceReference = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));

        if (authorityInstanceReference.getConnector() == null) {
            throw new NotFoundException("Connector associated with the Authority is not found. Unable to show details");
        }

        AuthorityProviderInstanceDto authorityProviderInstanceDto = authorityInstanceApiClient.getAuthorityInstance(authorityInstanceReference.getConnector().mapToDto(),
                authorityInstanceReference.getAuthorityInstanceUuid());

        AuthorityInstanceDto authorityInstanceDto = new AuthorityInstanceDto();
        authorityInstanceDto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(authorityProviderInstanceDto.getAttributes()));
        authorityInstanceDto.setName(authorityProviderInstanceDto.getName());
        authorityInstanceDto.setUuid(authorityInstanceReference.getUuid());
        authorityInstanceDto.setConnectorUuid(authorityInstanceReference.getConnector().getUuid());
        authorityInstanceDto.setKind(authorityInstanceReference.getKind());
        authorityInstanceDto.setConnectorName(authorityInstanceReference.getConnectorName());

        return authorityInstanceDto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.CREATE)
    public AuthorityInstanceDto createAuthorityInstance(com.czertainly.api.model.client.authority.AuthorityInstanceRequestDto request) throws AlreadyExistException, NotFoundException, ConnectorException {
        if (authorityInstanceReferenceRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(AuthorityInstanceReference.class, request.getName());
        }

        Connector connector = connectorService.getConnectorEntity(request.getConnectorUuid());

        FunctionGroupCode codeToSearch = FunctionGroupCode.AUTHORITY_PROVIDER;

        for (Connector2FunctionGroup function : connector.getFunctionGroups()) {
            if (function.getFunctionGroup().getCode() == FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER) {
                codeToSearch = FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER;
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
    public AuthorityInstanceDto updateAuthorityInstance(String uuid, AuthorityInstanceUpdateRequestDto request) throws NotFoundException, ConnectorException {
        AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));
        AuthorityInstanceDto ref = getAuthorityInstance(uuid);
        Connector connector = connectorService.getConnectorEntity(ref.getConnectorUuid());

        FunctionGroupCode codeToSearch = FunctionGroupCode.AUTHORITY_PROVIDER;

        for (Connector2FunctionGroup function : connector.getFunctionGroups()) {
            if (function.getFunctionGroup().getCode() == FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER) {
                codeToSearch = FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER;
            }
        }

        List<AttributeDefinition> attributes = connectorService.mergeAndValidateAttributes(connector.getUuid(), codeToSearch,
                request.getAttributes(), ref.getKind());

        // Load complete credential data
        credentialService.loadFullCredentialData(attributes);

        AuthorityProviderInstanceRequestDto authorityInstanceDto = new AuthorityProviderInstanceRequestDto();
        authorityInstanceDto.setAttributes(AttributeDefinitionUtils.getClientAttributes(attributes));
        authorityInstanceApiClient.updateAuthorityInstance(connector.mapToDto(),
                authorityInstanceRef.getAuthorityInstanceUuid(), authorityInstanceDto);
        authorityInstanceReferenceRepository.save(authorityInstanceRef);
        return authorityInstanceRef.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.DELETE)
    public void removeAuthorityInstance(String uuid) throws NotFoundException, ConnectorException {
        AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));

        List<ValidationError> errors = new ArrayList<>();
        if (!authorityInstanceRef.getRaProfiles().isEmpty()) {
            errors.add(ValidationError.create("Authority instance {} has {} dependent RA profiles", authorityInstanceRef.getName(),
                    authorityInstanceRef.getRaProfiles().size()));
            authorityInstanceRef.getRaProfiles().stream().forEach(c -> errors.add(ValidationError.create(c.getName())));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Could not delete Authority instance", errors);
        }

        authorityInstanceApiClient.removeAuthorityInstance(authorityInstanceRef.getConnector().mapToDto(), authorityInstanceRef.getAuthorityInstanceUuid());

        authorityInstanceReferenceRepository.delete(authorityInstanceRef);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.END_ENTITY_PROFILE, operation = OperationType.REQUEST)
    public List<NameAndIdDto> listEndEntityProfiles(String uuid) throws NotFoundException, ConnectorException {
        AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));

        return endEntityProfileApiClient.listEndEntityProfiles(authorityInstanceRef.getConnector().mapToDto(),
                authorityInstanceRef.getAuthorityInstanceUuid());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.END_ENTITY_PROFILE, operation = OperationType.REQUEST)
    public List<NameAndIdDto> listCertificateProfiles(String uuid, Integer endEntityProfileId) throws NotFoundException, ConnectorException {
        AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));

        return endEntityProfileApiClient.listCertificateProfiles(authorityInstanceRef.getConnector().mapToDto(),
                authorityInstanceRef.getAuthorityInstanceUuid(), endEntityProfileId);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.END_ENTITY_PROFILE, operation = OperationType.REQUEST)
    public List<NameAndIdDto> listCAsInProfile(String uuid, Integer endEntityProfileId) throws NotFoundException, ConnectorException {
        AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));

        return endEntityProfileApiClient.listCAsInProfile(authorityInstanceRef.getConnector().mapToDto(),
                authorityInstanceRef.getAuthorityInstanceUuid(), endEntityProfileId);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    public List<AttributeDefinition> listRAProfileAttributes(String uuid) throws NotFoundException, ConnectorException {
        AuthorityInstanceReference authorityInstance = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));
        Connector connector = authorityInstance.getConnector();

        return authorityInstanceApiClient.listRAProfileAttributes(connector.mapToDto(), authorityInstance.getAuthorityInstanceUuid());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.VALIDATE)
    public Boolean validateRAProfileAttributes(String uuid, List<RequestAttributeDto> attributes) throws NotFoundException, ConnectorException {
        AuthorityInstanceReference authorityInstance = authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));
        Connector connector = authorityInstance.getConnector();

        return authorityInstanceApiClient.validateRAProfileAttributes(connector.mapToDto(), authorityInstance.getAuthorityInstanceUuid(),
                attributes);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.DELETE)
    public List<ForceDeleteMessageDto> bulkRemoveAuthorityInstance(List<String> uuids) throws NotFoundException, ValidationException, ConnectorException {
        List<AuthorityInstanceReference> deletableCredentials = new ArrayList<>();
        List<ForceDeleteMessageDto> messages = new ArrayList<>();
        for (String uuid : uuids) {
            List<String> errors = new ArrayList<>();
            AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(uuid)
                    .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));

            if (!authorityInstanceRef.getRaProfiles().isEmpty()) {
                errors.add("RA Profiles: " + authorityInstanceRef.getRaProfiles().size() + ". Names: ");
                authorityInstanceRef.getRaProfiles().stream().forEach(c -> errors.add(c.getName()));
            }

            if (!errors.isEmpty()) {
                ForceDeleteMessageDto forceModal = new ForceDeleteMessageDto();
                forceModal.setUuid(authorityInstanceRef.getUuid());
                forceModal.setName(authorityInstanceRef.getName());
                forceModal.setMessage(String.join(",", errors));
                messages.add(forceModal);
            } else {
                deletableCredentials.add(authorityInstanceRef);
                try {
                    authorityInstanceApiClient.removeAuthorityInstance(authorityInstanceRef.getConnector().mapToDto(), authorityInstanceRef.getAuthorityInstanceUuid());
                }catch(ConnectorException e){
                    logger.error("Unable to delete authority with name {}", authorityInstanceRef.getName());
                }
            }
        }

        for (AuthorityInstanceReference authorityInstanceRef : deletableCredentials) {
            authorityInstanceReferenceRepository.delete(authorityInstanceRef);
        }
        return messages;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CA_INSTANCE, operation = OperationType.FORCE_DELETE)
    public void bulkForceRemoveAuthorityInstance(List<String> uuids) throws ValidationException, NotFoundException {
        for (String uuid : uuids) {
            try{
            AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(uuid)
                    .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));
            if (!authorityInstanceRef.getRaProfiles().isEmpty()) {
                for(RaProfile ref: authorityInstanceRef.getRaProfiles()){
                    ref.setAuthorityInstanceReference(null);
                    raProfileRepository.save(ref);
                }
            }
                authorityInstanceApiClient.removeAuthorityInstance(authorityInstanceRef.getConnector().mapToDto(), authorityInstanceRef.getAuthorityInstanceUuid());
            authorityInstanceReferenceRepository.delete(authorityInstanceRef);
        }catch (ConnectorException e){
                logger.warn("Unable to delete the Authority instance with uuid {}. It may have been deleted", uuid);
            }
        }
    }
}
