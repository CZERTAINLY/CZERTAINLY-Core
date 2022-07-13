package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.connector.ForceDeleteMessageDto;
import com.czertainly.api.model.client.credential.CredentialRequestDto;
import com.czertainly.api.model.client.credential.CredentialUpdateRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.AttributeCallback;
import com.czertainly.api.model.common.attribute.AttributeCallbackMapping;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.common.attribute.AttributeType;
import com.czertainly.api.model.common.attribute.AttributeValueTarget;
import com.czertainly.api.model.common.attribute.RequestAttributeCallback;
import com.czertainly.api.model.common.attribute.ResponseAttributeDto;
import com.czertainly.api.model.common.attribute.content.BaseAttributeContent;
import com.czertainly.api.model.common.attribute.content.JsonAttributeContent;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.credential.CredentialDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.Credential;
import com.czertainly.core.dao.repository.AuthorityInstanceReferenceRepository;
import com.czertainly.core.dao.repository.CredentialRepository;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.service.CredentialService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
@Secured({"ROLE_ADMINISTRATOR", "ROLE_SUPERADMINISTRATOR"})
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
    public List<CredentialDto> listCredentials() {
        return credentialRepository.findAll().stream()
                .map(Credential::mapToDtoSimple)
                .collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.REQUEST)
    public CredentialDto getCredential(String uuid) throws NotFoundException {
        return maskSecret(getCredentialEntity(uuid).mapToDto());

    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.REQUEST)
    public Credential getCredentialEntity(Long id) throws NotFoundException {
        return credentialRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(Credential.class, id));
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.REQUEST)
    public Credential getCredentialEntity(String uuid) throws NotFoundException {
        return credentialRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Credential.class, uuid));
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.CREATE)
    public CredentialDto createCredential(CredentialRequestDto request) throws AlreadyExistException, ConnectorException {
        if (StringUtils.isBlank(request.getName())) {
            throw new ValidationException("Name must not be empty");
        }

        if (credentialRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(Credential.class, request.getName());
        }

        Connector connector = connectorService.getConnectorEntity(request.getConnectorUuid());

        List<AttributeDefinition> attributes = connectorService.mergeAndValidateAttributes(
                connector.getUuid(),
                FunctionGroupCode.CREDENTIAL_PROVIDER,
                request.getAttributes(), request.getKind());

        Credential credential = new Credential();
        credential.setName(request.getName());
        credential.setKind(request.getKind());
        credential.setAttributes(AttributeDefinitionUtils.serialize(attributes));
        credential.setEnabled(true);
        credential.setConnector(connector);
        credential.setConnectorName(connector.getName());
        credentialRepository.save(credential);

        return credential.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.CHANGE)
    public CredentialDto updateCredential(String uuid, CredentialUpdateRequestDto request) throws ConnectorException {
        Credential credential = credentialRepository
                .findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Credential.class, uuid));
        Credential requestedCredential = getCredentialEntity(uuid);

        Connector connector = requestedCredential.getConnector();

        if (!credential.getConnector().equals(connector)) {
            throw new ValidationException(ValidationError.create("Credential provider id not matched."));
        }

        List<AttributeDefinition> attributes = connectorService.mergeAndValidateAttributes(
                connector.getUuid(),
                FunctionGroupCode.CREDENTIAL_PROVIDER,
                request.getAttributes(), credential.getKind());

        credential.setAttributes(AttributeDefinitionUtils.serialize(attributes));
        credentialRepository.save(credential);

        return credential.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.DELETE)
    public void removeCredential(String uuid) throws NotFoundException {
        Credential credential = credentialRepository
                .findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Credential.class, uuid));

        credentialRepository.delete(credential);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.ENABLE)
    public void enableCredential(String uuid) throws NotFoundException {
        Credential credential = credentialRepository
                .findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Credential.class, uuid));
        credential.setEnabled(true);
        credentialRepository.save(credential);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.DISABLE)
    public void disableCredential(String uuid) throws NotFoundException {
        Credential credential = credentialRepository
                .findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Credential.class, uuid));
        credential.setEnabled(false);
        credentialRepository.save(credential);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.DELETE)
    public List<ForceDeleteMessageDto> bulkRemoveCredential(List<String> uuids) throws ValidationException, NotFoundException {
        List<Credential> deletableCredentials = new ArrayList<>();
        List<ForceDeleteMessageDto> messages = new ArrayList<>();
        for (String uuid : uuids) {
            List<String> errors = new ArrayList<>();
            Credential credential = credentialRepository
                    .findByUuid(uuid)
                    .orElseThrow(() -> new NotFoundException(Credential.class, uuid));

            if (!errors.isEmpty()) {
                ForceDeleteMessageDto forceModal = new ForceDeleteMessageDto();
                forceModal.setUuid(credential.getUuid());
                forceModal.setName(credential.getName());
                forceModal.setMessage(String.join(",", errors));
                messages.add(forceModal);
            } else {
                deletableCredentials.add(credential);
            }
        }
        for (Credential credential : deletableCredentials) {
            credentialRepository.delete(credential);
        }
        return messages;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.FORCE_DELETE)
    public void bulkForceRemoveCredential(List<String> uuids) throws ValidationException, NotFoundException {
        for (String uuid : uuids) {
            try {
                Credential credential = credentialRepository
                        .findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(Credential.class, uuid));
                credentialRepository.delete(credential);
            } catch (NotFoundException e) {
                logger.warn("Unable to find Credential with uuid {}. It may have deleted", uuid);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.BE, affected = ObjectType.CREDENTIAL, operation = OperationType.REQUEST)
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
            Credential credential = getCredentialEntity(credentialId.getUuid());
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

                                NameAndUuidDto nameAndUuidDto;

                                if (bodyKeyValue instanceof NameAndUuidDto) {
                                    nameAndUuidDto = (NameAndUuidDto) bodyKeyValue;
                                } else if (bodyKeyValue instanceof Map) {
                                    try {
                                        nameAndUuidDto = new ObjectMapper().convertValue(bodyKeyValue, NameAndUuidDto.class);
                                    } catch (Exception e) {
                                        logger.error(e.getMessage(), e);
                                        throw new ValidationException(ValidationError.create(
                                                "Invalid value {}, because of {}.", bodyKeyValue, e.getMessage()));
                                    }
                                } else {
                                    throw new ValidationException(ValidationError.create(
                                            "Invalid value {}. Instance of {} is expected.", bodyKeyValue, NameAndUuidDto.class));
                                }

                                CredentialDto credential = getCredentialEntity(nameAndUuidDto.getUuid()).mapToDto();
                                requestAttributeCallback.getRequestBody().put(mapping.getTo(), credential);
                                break;
                        }
                    }
                }
            }
        }
    }

    private CredentialDto maskSecret(CredentialDto credentialDto){
        for(ResponseAttributeDto responseAttributeDto: credentialDto.getAttributes()){
            if(TO_BE_MASKED.contains(responseAttributeDto.getType())){
                responseAttributeDto.setContent(new BaseAttributeContent<String>(){{setValue(null);}});
            }
        }
        return credentialDto;
    }
}
