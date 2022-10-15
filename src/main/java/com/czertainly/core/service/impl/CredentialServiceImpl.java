package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.credential.CredentialRequestDto;
import com.czertainly.api.model.client.credential.CredentialUpdateRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.*;
import com.czertainly.api.model.common.attribute.content.BaseAttributeContent;
import com.czertainly.api.model.common.attribute.content.JsonAttributeContent;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.credential.CredentialDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Credential;
import com.czertainly.core.dao.repository.AuthorityInstanceReferenceRepository;
import com.czertainly.core.dao.repository.CredentialRepository;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.service.CredentialService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class CredentialServiceImpl implements CredentialService {

    private static final Logger logger = LoggerFactory.getLogger(CredentialServiceImpl.class);
    private static final List<AttributeType> TO_BE_MASKED = List.of(AttributeType.SECRET);

    @Autowired
    private CredentialRepository credentialRepository;
    @Autowired
    private ConnectorService connectorService;
    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;


    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CREDENTIAL, action = ResourceAction.LIST)
    public List<CredentialDto> listCredentials(SecurityFilter filter) {
        return credentialRepository.findUsingSecurityFilter(filter).stream()
                .map(Credential::mapToDtoSimple)
                .collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CREDENTIAL, action = ResourceAction.LIST)
    public List<NameAndUuidDto> listCredentialsCallback(SecurityFilter filter, String kind) throws NotFoundException {
        List<Credential> credentials = credentialRepository.findUsingSecurityFilter(
                filter,
                (root, cb) -> cb.and(cb.equal(root.get("enabled"), true), cb.equal(root.get("kind"), kind)));

        if (credentials == null || credentials.isEmpty()) {
            throw new NotFoundException(Credential.class, kind);
        }

        List<NameAndUuidDto> credentialDataList = credentials.stream()
                .map(c -> new NameAndUuidDto(c.getUuid().toString(), c.getName()))
                .collect(Collectors.toList());

        return credentialDataList;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CREDENTIAL, action = ResourceAction.DETAIL)
    public CredentialDto getCredential(SecuredUUID uuid) throws NotFoundException {
        return maskSecret(getCredentialEntity(uuid).mapToDto());

    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.CREATE)
    @ExternalAuthorization(resource = Resource.CREDENTIAL, action = ResourceAction.CREATE)
    public CredentialDto createCredential(CredentialRequestDto request) throws AlreadyExistException, ConnectorException {
        if (StringUtils.isBlank(request.getName())) {
            throw new ValidationException("Name must not be empty");
        }

        if (credentialRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(Credential.class, request.getName());
        }

        SecuredUUID connectorUuid = SecuredUUID.fromString(request.getConnectorUuid());
        ConnectorDto connector = connectorService.getConnectorEntity(connectorUuid).mapToDto();

        List<AttributeDefinition> attributes = connectorService.mergeAndValidateAttributes(
                connectorUuid,
                FunctionGroupCode.CREDENTIAL_PROVIDER,
                request.getAttributes(), request.getKind());

        Credential credential = new Credential();
        credential.setName(request.getName());
        credential.setKind(request.getKind());
        credential.setAttributes(AttributeDefinitionUtils.serialize(attributes));
        credential.setEnabled(true);
        credential.setConnectorUuid(UUID.fromString(request.getConnectorUuid()));
        credential.setConnectorName(connector.getName());
        credentialRepository.save(credential);

        return credential.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.CREDENTIAL, action = ResourceAction.UPDATE)
    public CredentialDto editCredential(SecuredUUID uuid, CredentialUpdateRequestDto request) throws ConnectorException {
        Credential credential = getCredentialEntity(uuid);
        SecuredUUID connectorUuid = SecuredUUID.fromUUID(credential.getConnectorUuid());

        List<AttributeDefinition> attributes = connectorService.mergeAndValidateAttributes(
                connectorUuid,
                FunctionGroupCode.CREDENTIAL_PROVIDER,
                request.getAttributes(), credential.getKind());

        credential.setAttributes(AttributeDefinitionUtils.serialize(attributes));
        credentialRepository.save(credential);

        return credential.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.CREDENTIAL, action = ResourceAction.DELETE)
    public void deleteCredential(SecuredUUID uuid) throws NotFoundException {
        Credential credential = getCredentialEntity(uuid);

        credentialRepository.delete(credential);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.ENABLE)
    @ExternalAuthorization(resource = Resource.CREDENTIAL, action = ResourceAction.ENABLE)
    public void enableCredential(SecuredUUID uuid) throws NotFoundException {
        Credential credential = getCredentialEntity(uuid);
        credential.setEnabled(true);
        credentialRepository.save(credential);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.DISABLE)
    @ExternalAuthorization(resource = Resource.CREDENTIAL, action = ResourceAction.ENABLE)
    public void disableCredential(SecuredUUID uuid) throws NotFoundException {
        Credential credential = getCredentialEntity(uuid);
        credential.setEnabled(false);
        credentialRepository.save(credential);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.CREDENTIAL, action = ResourceAction.DELETE)
    public void bulkDeleteCredential(List<SecuredUUID> uuids) throws ValidationException, NotFoundException {
        for (SecuredUUID uuid : uuids) {
            try {
                deleteCredential(uuid);
            } catch (NotFoundException e) {
                logger.warn("Unable to find Credential with uuid {}. It may have deleted", uuid);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.BE, affected = ObjectType.CREDENTIAL, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CREDENTIAL, action = ResourceAction.DETAIL)
    public void loadFullCredentialData(List<AttributeDefinition> attributes) throws NotFoundException {
        if (attributes == null || attributes.isEmpty()) {
            logger.warn("Given Attributes are null or empty");
            return;
        }

        for (AttributeDefinition attribute : attributes) {
            if (!AttributeType.CREDENTIAL.equals(attribute.getType())) {
                logger.trace("Attribute not of type {} but {}.", AttributeType.CREDENTIAL, attribute.getType());
                continue;
            }

            NameAndUuidDto credentialId = AttributeDefinitionUtils.getNameAndUuidData(attribute.getName(), AttributeDefinitionUtils.getClientAttributes(attributes));
            Credential credential = getCredentialEntity(SecuredUUID.fromString(credentialId.getUuid()));
            attribute.setContent(new JsonAttributeContent(credentialId.getName(), credential.mapToDto()));
            logger.debug("Value of Credential Attribute {} updated.", attribute.getName());
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.BE, affected = ObjectType.CREDENTIAL, operation = OperationType.REQUEST)
    public void loadFullCredentialData(AttributeCallback callback, RequestAttributeCallback requestAttributeCallback) throws NotFoundException {
        if (callback == null) {
            logger.warn("Given Callback is null");
            return;
        }

        if (callback.getMappings() != null) {
            for (AttributeCallbackMapping mapping : callback.getMappings()) {
                if (AttributeType.CREDENTIAL.equals(mapping.getAttributeType())) {
                    for (AttributeValueTarget target : mapping.getTargets()) {
                        switch (target) {
                            case PATH_VARIABLE:
                            case REQUEST_PARAMETER:
                                logger.warn("Illegal 'from' Attribute type {} for target {}",
                                        mapping.getAttributeType(), target);
                                break;
                            case BODY:
                                logger.info("Found 'from' Attribute type {} for target {}, going to load full Credential data",
                                        mapping.getAttributeType(), target);

                                Serializable bodyKeyValue = requestAttributeCallback.getRequestBody().get(mapping.getTo());

                                String credentialUuid;
                                if (bodyKeyValue instanceof NameAndUuidDto) {
                                    credentialUuid = ((NameAndUuidDto) bodyKeyValue).getUuid();
                                }
                                else if (bodyKeyValue instanceof Map) {
                                    try {
                                        credentialUuid = (String) ((Map) (new ObjectMapper().convertValue(bodyKeyValue, JsonAttributeContent.class)).getData()).get("uuid");
                                    } catch (Exception e) {
                                        logger.error(e.getMessage(), e);
                                        throw new ValidationException(ValidationError.create(
                                                "Invalid value {}, because of {}.", bodyKeyValue, e.getMessage()));
                                    }
                                } else {
                                    throw new ValidationException(ValidationError.create(
                                            "Invalid value {}. Instance of {} is expected.", bodyKeyValue, NameAndUuidDto.class));
                                }

                                CredentialDto credential = getCredentialEntity(SecuredUUID.fromString(credentialUuid)).mapToDto();
                                requestAttributeCallback.getRequestBody().put(mapping.getTo(), credential);
                                break;
                        }
                    }
                }
            }
        }
    }

    private Credential getCredentialEntity(SecuredUUID uuid) throws NotFoundException {
        return credentialRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Credential.class, uuid));
    }

    private CredentialDto maskSecret(CredentialDto credentialDto){
        for(ResponseAttributeDto responseAttributeDto: credentialDto.getAttributes()){
            if(TO_BE_MASKED.contains(responseAttributeDto.getType())){
                responseAttributeDto.setContent(new BaseAttributeContent<String>(null));
            }
        }
        return credentialDto;
    }
}
