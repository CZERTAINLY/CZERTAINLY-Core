package com.czertainly.core.service.impl;

import com.czertainly.api.clients.AuthorityInstanceApiClient;
import com.czertainly.api.clients.EndEntityProfileApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.client.authority.AuthorityInstanceUpdateRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.NameAndIdDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.connector.authority.AuthorityProviderInstanceDto;
import com.czertainly.api.model.connector.authority.AuthorityProviderInstanceRequestDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.authority.AuthorityInstanceDto;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.AuthorityInstanceReference;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.Connector2FunctionGroup;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.AuthorityInstanceReferenceRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AuthorityInstanceService;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.service.CredentialService;
import com.czertainly.core.service.RaProfileService;
import com.czertainly.core.util.AttributeDefinitionUtils;
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
    private AttributeEngine attributeEngine;

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }


    @Override
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.LIST)
    public List<AuthorityInstanceDto> listAuthorityInstances(SecurityFilter filter) {
        return authorityInstanceReferenceRepository.findUsingSecurityFilter(filter)
                .stream()
                .map(AuthorityInstanceReference::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.DETAIL)
    public AuthorityInstanceDto getAuthorityInstance(SecuredUUID uuid) throws ConnectorException {
        AuthorityInstanceReference authorityInstanceReference = getAuthorityInstanceReferenceEntity(uuid);

        List<ResponseAttributeDto> attributes = attributeEngine.getObjectDataAttributesContent(authorityInstanceReference.getConnectorUuid(), null, Resource.AUTHORITY, authorityInstanceReference.getUuid());

        AuthorityInstanceDto authorityInstanceDto = authorityInstanceReference.mapToDto();
        authorityInstanceDto.setCustomAttributes(attributeEngine.getObjectCustomAttributesContent(Resource.AUTHORITY, uuid.getValue()));
        if (authorityInstanceReference.getConnector() == null) {
            authorityInstanceDto.setConnectorName(authorityInstanceReference.getConnectorName() + " (Deleted)");
            authorityInstanceDto.setConnectorUuid("");
            authorityInstanceDto.setAttributes(attributes);
            logger.warn("Connector associated with the Authority: {} is not found. Unable to show details", authorityInstanceReference.getName());

            return authorityInstanceDto;
        }

        AuthorityProviderInstanceDto authorityProviderInstanceDto = authorityInstanceApiClient.getAuthorityInstance(authorityInstanceReference.getConnector().mapToDto(),
                authorityInstanceReference.getAuthorityInstanceUuid());

        if (attributes.isEmpty() && authorityProviderInstanceDto.getAttributes() != null && !authorityProviderInstanceDto.getAttributes().isEmpty()) {
            try {
                List<RequestAttributeDto> requestAttributes = AttributeDefinitionUtils.getClientAttributes(authorityProviderInstanceDto.getAttributes());
                attributeEngine.updateDataAttributeDefinitions(authorityInstanceReference.getConnectorUuid(), null, authorityProviderInstanceDto.getAttributes());
                attributes = attributeEngine.updateObjectDataAttributesContent(authorityInstanceReference.getConnectorUuid(), null, Resource.AUTHORITY, authorityInstanceReference.getUuid(), requestAttributes);
            } catch (AttributeException e) {
                logger.warn("Could not update data attributes for authority {} retrieved from connector", authorityInstanceReference.getName());
            }
        }

        authorityInstanceDto.setAttributes(attributes);
        return authorityInstanceDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.CREATE)
    public AuthorityInstanceDto createAuthorityInstance(com.czertainly.api.model.client.authority.AuthorityInstanceRequestDto request) throws AlreadyExistException, ConnectorException, AttributeException {
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
        attributeEngine.validateCustomAttributesContent(Resource.AUTHORITY, request.getCustomAttributes());
        connectorService.mergeAndValidateAttributes(connector.getSecuredUuid(), codeToSearch, request.getAttributes(), request.getKind());

        // Load complete credential data
        var dataAttributes = attributeEngine.getDataAttributesByContent(connector.getUuid(), request.getAttributes());
        credentialService.loadFullCredentialData(dataAttributes);

        AuthorityProviderInstanceRequestDto authorityInstanceDto = new AuthorityProviderInstanceRequestDto();
        authorityInstanceDto.setAttributes(AttributeDefinitionUtils.getClientAttributes(dataAttributes));
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

        AuthorityInstanceDto dto = authorityInstanceRef.mapToDto();
        dto.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.AUTHORITY, authorityInstanceRef.getUuid(), request.getCustomAttributes()));
        dto.setAttributes(attributeEngine.updateObjectDataAttributesContent(authorityInstanceRef.getConnectorUuid(), null, Resource.AUTHORITY, authorityInstanceRef.getUuid(), request.getAttributes()));
        return authorityInstanceRef.mapToDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.UPDATE)
    public AuthorityInstanceDto editAuthorityInstance(SecuredUUID uuid, AuthorityInstanceUpdateRequestDto request) throws ConnectorException, AttributeException {
        AuthorityInstanceReference authorityInstanceRef = getAuthorityInstanceReferenceEntity(uuid);
        AuthorityInstanceDto ref = getAuthorityInstance(uuid);
        Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromString(ref.getConnectorUuid()));

        FunctionGroupCode codeToSearch = FunctionGroupCode.AUTHORITY_PROVIDER;

        for (Connector2FunctionGroup function : connector.getFunctionGroups()) {
            if (function.getFunctionGroup().getCode() == FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER) {
                codeToSearch = FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER;
                break;
            }
        }
        attributeEngine.validateCustomAttributesContent(Resource.AUTHORITY, request.getCustomAttributes());
        connectorService.mergeAndValidateAttributes(connector.getSecuredUuid(), codeToSearch, request.getAttributes(), ref.getKind());

        // Load complete credential data
        var dataAttributes = attributeEngine.getDataAttributesByContent(connector.getUuid(), request.getAttributes());
        credentialService.loadFullCredentialData(dataAttributes);

        AuthorityProviderInstanceRequestDto authorityInstanceDto = new AuthorityProviderInstanceRequestDto();
        authorityInstanceDto.setKind(ref.getKind());
        authorityInstanceDto.setName(ref.getName());
        authorityInstanceDto.setAttributes(AttributeDefinitionUtils.getClientAttributes(dataAttributes));
        authorityInstanceApiClient.updateAuthorityInstance(connector.mapToDto(),
                authorityInstanceRef.getAuthorityInstanceUuid(), authorityInstanceDto);
        authorityInstanceReferenceRepository.save(authorityInstanceRef);

        AuthorityInstanceDto dto = authorityInstanceRef.mapToDto();
        dto.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.AUTHORITY, authorityInstanceRef.getUuid(), request.getCustomAttributes()));
        dto.setAttributes(attributeEngine.updateObjectDataAttributesContent(authorityInstanceRef.getConnectorUuid(), null, Resource.AUTHORITY, authorityInstanceRef.getUuid(), request.getAttributes()));

        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.DELETE)
    public void deleteAuthorityInstance(SecuredUUID uuid) throws ConnectorException {
        AuthorityInstanceReference authorityInstanceRef = getAuthorityInstanceReferenceEntity(uuid);
        removeAuthorityInstance(authorityInstanceRef);
    }

    @Override
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.DETAIL)
    public List<NameAndIdDto> listEndEntityProfiles(SecuredUUID uuid) throws ConnectorException {
        AuthorityInstanceReference authorityInstanceRef = getAuthorityInstanceReferenceEntity(uuid);

        return endEntityProfileApiClient.listEndEntityProfiles(authorityInstanceRef.getConnector().mapToDto(),
                authorityInstanceRef.getAuthorityInstanceUuid());
    }

    @Override
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.DETAIL)
    public List<NameAndIdDto> listCertificateProfiles(SecuredUUID uuid, Integer endEntityProfileId) throws ConnectorException {
        AuthorityInstanceReference authorityInstanceRef = getAuthorityInstanceReferenceEntity(uuid);

        return endEntityProfileApiClient.listCertificateProfiles(authorityInstanceRef.getConnector().mapToDto(),
                authorityInstanceRef.getAuthorityInstanceUuid(), endEntityProfileId);
    }

    @Override
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.DETAIL)
    public List<NameAndIdDto> listCAsInProfile(SecuredUUID uuid, Integer endEntityProfileId) throws ConnectorException {
        AuthorityInstanceReference authorityInstanceRef = getAuthorityInstanceReferenceEntity(uuid);

        return endEntityProfileApiClient.listCAsInProfile(authorityInstanceRef.getConnector().mapToDto(),
                authorityInstanceRef.getAuthorityInstanceUuid(), endEntityProfileId);
    }

    @Override
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.ANY)
    public List<BaseAttribute> listRAProfileAttributes(SecuredUUID uuid) throws ConnectorException {
        AuthorityInstanceReference authorityInstance = getAuthorityInstanceReferenceEntity(uuid);
        Connector connector = authorityInstance.getConnector();

        return authorityInstanceApiClient.listRAProfileAttributes(connector.mapToDto(), authorityInstance.getAuthorityInstanceUuid());
    }

    @Override
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.ANY)
    public Boolean validateRAProfileAttributes(SecuredUUID uuid, List<RequestAttributeDto> attributes) throws ConnectorException {
        AuthorityInstanceReference authorityInstance = getAuthorityInstanceReferenceEntity(uuid);
        Connector connector = authorityInstance.getConnector();

        return authorityInstanceApiClient.validateRAProfileAttributes(connector.mapToDto(), authorityInstance.getAuthorityInstanceUuid(),
                attributes);
    }

    @Override
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

    @Override
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.LIST)
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter) {
        return authorityInstanceReferenceRepository.findUsingSecurityFilter(filter)
                .stream()
                .map(AuthorityInstanceReference::mapToAccessControlObjects)
                .collect(Collectors.toList());
    }

    @Override
    @ExternalAuthorization(resource = Resource.AUTHORITY, action = ResourceAction.UPDATE)
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {
        getAuthorityInstanceReferenceEntity(uuid);
        // Since there are is no parent to the Authority, exclusive parent permission evaluation need not be done
    }

    private AuthorityInstanceReference getAuthorityInstanceReferenceEntity(SecuredUUID uuid) throws NotFoundException {
        return authorityInstanceReferenceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, uuid));
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
            } catch (NotFoundException notFoundException) {
                logger.warn("Authority is already deleted in the connector. Proceeding to remove it from the core");
            } catch (Exception e) {
                logger.error(e.getMessage());
                throw new ValidationException(e.getMessage());
            }
        } else {
            logger.debug("Deleting authority without connector: {}", authorityInstanceRef);
        }
        attributeEngine.deleteAllObjectAttributeContent(Resource.AUTHORITY, authorityInstanceRef.getUuid());
        authorityInstanceReferenceRepository.delete(authorityInstanceRef);
    }
}
