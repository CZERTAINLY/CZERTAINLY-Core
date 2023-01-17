package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.credential.CredentialRequestDto;
import com.czertainly.api.model.client.credential.CredentialUpdateRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.callback.AttributeCallback;
import com.czertainly.api.model.common.attribute.v2.callback.AttributeCallbackMapping;
import com.czertainly.api.model.common.attribute.v2.callback.AttributeValueTarget;
import com.czertainly.api.model.common.attribute.v2.callback.RequestAttributeCallback;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.CredentialAttributeContent;
import com.czertainly.api.model.common.attribute.v2.content.ObjectAttributeContent;
import com.czertainly.api.model.common.attribute.v2.content.data.CredentialAttributeContentData;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.credential.CredentialDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Credential;
import com.czertainly.core.dao.repository.AuthorityInstanceReferenceRepository;
import com.czertainly.core.dao.repository.CredentialRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AttributeService;
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
    private static final List<AttributeContentType> TO_BE_MASKED = List.of(AttributeContentType.SECRET);

    @Autowired
    private CredentialRepository credentialRepository;
    @Autowired
    private ConnectorService connectorService;
    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;
    @Autowired
    private AttributeService attributeService;


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
        CredentialDto credentialDto = getCredentialEntity(uuid).mapToDto();
        credentialDto.setCustomAttributes(attributeService.getCustomAttributesWithValues(uuid.getValue(), Resource.CREDENTIAL));
        return credentialDto;
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

        attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.CREDENTIAL);
        List<DataAttribute> attributes = connectorService.mergeAndValidateAttributes(
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

        attributeService.createAttributeContent(credential.getUuid(), request.getCustomAttributes(), Resource.CREDENTIAL);

        CredentialDto credentialDto = credential.mapToDto();
        credentialDto.setCustomAttributes(attributeService.getCustomAttributesWithValues(credential.getUuid(), Resource.CREDENTIAL));

        return credential.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.CREDENTIAL, action = ResourceAction.UPDATE)
    public CredentialDto editCredential(SecuredUUID uuid, CredentialUpdateRequestDto request) throws ConnectorException {
        Credential credential = getCredentialEntity(uuid);
        SecuredUUID connectorUuid = SecuredUUID.fromUUID(credential.getConnectorUuid());
        attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.CREDENTIAL);
        List<DataAttribute> attributes = connectorService.mergeAndValidateAttributes(
                connectorUuid,
                FunctionGroupCode.CREDENTIAL_PROVIDER,
                request.getAttributes(), credential.getKind());

        credential.setAttributes(AttributeDefinitionUtils.serialize(attributes));
        credentialRepository.save(credential);
        attributeService.updateAttributeContent(credential.getUuid(), request.getCustomAttributes(), Resource.CREDENTIAL);

        CredentialDto credentialDto = credential.mapToDto();
        credentialDto.setCustomAttributes(attributeService.getCustomAttributesWithValues(credential.getUuid(), Resource.CREDENTIAL));

        return credentialDto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CREDENTIAL, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.CREDENTIAL, action = ResourceAction.DELETE)
    public void deleteCredential(SecuredUUID uuid) throws NotFoundException {
        Credential credential = getCredentialEntity(uuid);
        attributeService.deleteAttributeContent(uuid.getValue(), Resource.CREDENTIAL);
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
    public void loadFullCredentialData(List<DataAttribute> attributes) throws NotFoundException {
        if (attributes == null || attributes.isEmpty()) {
            logger.warn("Given Attributes are null or empty");
            return;
        }

        for (DataAttribute attribute : attributes) {
            if (!AttributeContentType.CREDENTIAL.equals(attribute.getContentType())) {
                logger.trace("Attribute not of type {} but {}.", AttributeContentType.CREDENTIAL, attribute.getType());
                continue;
            }

            NameAndUuidDto credentialId = AttributeDefinitionUtils.getNameAndUuidData(attribute.getName(), AttributeDefinitionUtils.getClientAttributes(attributes));
            Credential credential = getCredentialEntity(SecuredUUID.fromString(credentialId.getUuid()));
            attribute.setContent(List.of(new CredentialAttributeContent(credentialId.getName(), credential.mapToCredentialContent())));
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
                if (AttributeContentType.CREDENTIAL.equals(mapping.getAttributeContentType())) {
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

                                Serializable bodyKeyValue = requestAttributeCallback.getBody().get(mapping.getTo());

                                String credentialUuid;
                                String referenceName;
                                if (bodyKeyValue instanceof NameAndUuidDto) {
                                    credentialUuid = ((NameAndUuidDto) bodyKeyValue).getUuid();
                                    referenceName = ((NameAndUuidDto) bodyKeyValue).getName();
                                } else if (bodyKeyValue instanceof CredentialDto) {
                                    credentialUuid = ((CredentialDto) bodyKeyValue).getUuid();
                                    referenceName = ((CredentialDto) bodyKeyValue).getName();
                                } else if (bodyKeyValue instanceof CredentialAttributeContent) {
                                    credentialUuid = ((List<CredentialAttributeContent>) bodyKeyValue).get(0).getData().getUuid();
                                    referenceName = ((List<CredentialAttributeContent>) bodyKeyValue).get(0).getReference();
                                } else if (bodyKeyValue instanceof List && ((List<?>) bodyKeyValue).get(0) instanceof CredentialAttributeContent) {
                                    credentialUuid = ((List<CredentialAttributeContent>) bodyKeyValue).get(0).getData().getUuid();
                                    referenceName = ((List<CredentialAttributeContent>) bodyKeyValue).get(0).getReference();
                                } else if (bodyKeyValue instanceof Map) {
                                    if(((Map) bodyKeyValue).containsKey("uuid")) {
                                        credentialUuid = (String) ((Map) bodyKeyValue).get("uuid");
                                        referenceName = (String) ((Map) bodyKeyValue).get("name");
                                    } else {
                                        try {
                                            credentialUuid = (String) ((Map) (new ObjectMapper().convertValue(bodyKeyValue, ObjectAttributeContent.class)).getData()).get("uuid");
                                            referenceName = credentialUuid = (String) ((Map) (new ObjectMapper().convertValue(bodyKeyValue, ObjectAttributeContent.class)).getData()).get("name");
                                        } catch (Exception e) {
                                            logger.error(e.getMessage(), e);
                                            throw new ValidationException(ValidationError.create(
                                                    "Invalid value {}, because of {}.", bodyKeyValue, e.getMessage()));
                                        }
                                    }
                                } else {
                                    throw new ValidationException(ValidationError.create(
                                            "Invalid value {}. Instance of {} is expected.", bodyKeyValue, NameAndUuidDto.class));
                                }

                                CredentialAttributeContentData credential = getCredentialEntity(SecuredUUID.fromString(credentialUuid)).mapToCredentialContent();
                                requestAttributeCallback.getBody().put(mapping.getTo(), credential);
                                break;
                        }
                    }
                }
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.CREDENTIAL, action = ResourceAction.LIST)
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter) {
        return credentialRepository.findUsingSecurityFilter(filter)
                .stream()
                .map(Credential::mapToAccessControlObjects)
                .collect(Collectors.toList());
    }

    private Credential getCredentialEntity(SecuredUUID uuid) throws NotFoundException {
        return credentialRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Credential.class, uuid));
    }
}
