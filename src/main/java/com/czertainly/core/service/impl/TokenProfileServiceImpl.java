package com.czertainly.core.service.impl;

import com.czertainly.api.clients.cryptography.TokenInstanceApiClient;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.cryptography.tokenprofile.AddTokenProfileRequestDto;
import com.czertainly.api.model.client.cryptography.tokenprofile.EditTokenProfileRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cryptography.key.KeyUsage;
import com.czertainly.api.model.core.cryptography.tokenprofile.TokenProfileDetailDto;
import com.czertainly.api.model.core.cryptography.tokenprofile.TokenProfileDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.TokenInstanceReference;
import com.czertainly.core.dao.entity.TokenProfile;
import com.czertainly.core.dao.repository.TokenInstanceReferenceRepository;
import com.czertainly.core.dao.repository.TokenProfileRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AttributeService;
import com.czertainly.core.service.TokenProfileService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class TokenProfileServiceImpl implements TokenProfileService {

    private static final Logger logger = LoggerFactory.getLogger(TokenProfileServiceImpl.class);


    // --------------------------------------------------------------------------------
    // Services & API Clients
    // --------------------------------------------------------------------------------
    private AttributeService attributeService;
    private TokenInstanceApiClient tokenInstanceApiClient;
    // --------------------------------------------------------------------------------
    // Repositories
    // --------------------------------------------------------------------------------
    private TokenProfileRepository tokenProfileRepository;
    private TokenInstanceReferenceRepository tokenInstanceReferenceRepository;


    @Autowired
    public void setTokenProfileRepository(TokenProfileRepository tokenProfileRepository) {
        this.tokenProfileRepository = tokenProfileRepository;
    }

    @Autowired
    public void setAttributeService(AttributeService attributeService) {
        this.attributeService = attributeService;
    }

    @Autowired
    public void setTokenInstanceReferenceRepository(TokenInstanceReferenceRepository tokenInstanceReferenceRepository) {
        this.tokenInstanceReferenceRepository = tokenInstanceReferenceRepository;
    }

    @Autowired
    public void setTokenInstanceApiClient(TokenInstanceApiClient tokenInstanceApiClient) {
        this.tokenInstanceApiClient = tokenInstanceApiClient;
    }

    //-------------------------------------------------------------------------------------
    //Service Implementations
    //-------------------------------------------------------------------------------------
    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_PROFILE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.TOKEN_PROFILE, action = ResourceAction.LIST, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public List<TokenProfileDto> listTokenProfiles(Optional<Boolean> enabled, SecurityFilter filter) {
        logger.info("Listing token profiles");
        filter.setParentRefProperty("tokenInstanceReferenceUuid");
        if (enabled == null || !enabled.isPresent()) {
            return tokenProfileRepository.findUsingSecurityFilter(filter)
                    .stream()
                    .map(TokenProfile::mapToDto)
                    .collect(Collectors.toList());
        } else {
            return tokenProfileRepository.findUsingSecurityFilter(filter, enabled.get())
                    .stream()
                    .map(TokenProfile::mapToDto)
                    .collect(Collectors.toList());
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_PROFILE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.TOKEN_PROFILE, action = ResourceAction.DETAIL, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public TokenProfileDetailDto getTokenProfile(SecuredParentUUID tokenInstanceUuid, SecuredUUID uuid) throws NotFoundException {
        logger.info("Getting token profile with uuid: {}", uuid);
        TokenProfile tokenProfile = getTokenProfileEntity(uuid);
        TokenProfileDetailDto dto = tokenProfile.mapToDetailDto();
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(tokenProfile.getUuid(), Resource.TOKEN_PROFILE));
        logger.debug("Token profile detail: {}", dto);
        return dto;
    }

    @Override
    public TokenProfileDetailDto createTokenProfile(SecuredParentUUID tokenInstanceUuid, AddTokenProfileRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException {
        logger.info("Creating token profile with name: {}", request.getName());
        if (StringUtils.isBlank(request.getName())) {
            throw new ValidationException("Token Profile name must not be empty");
        }

        Optional<TokenProfile> optionalProfile = tokenProfileRepository.findByName(request.getName());
        if (optionalProfile.isPresent()) {
            throw new AlreadyExistException(TokenProfile.class, request.getName());
        }
        logger.debug("Validating the custom attributes for the new token profile");
        attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.TOKEN_PROFILE);
        TokenInstanceReference tokenInstanceReference = tokenInstanceReferenceRepository.findByUuid(tokenInstanceUuid)
                .orElseThrow(() -> new NotFoundException(TokenInstanceReferenceRepository.class, tokenInstanceUuid));
        logger.debug("Token Instance Reference found: {}", tokenInstanceReference);
        List<DataAttribute> attributes = mergeAndValidateAttributes(tokenInstanceReference, request.getAttributes());
        TokenProfile tokenProfile = createTokenProfile(request, attributes, tokenInstanceReference);
        logger.debug("Token profile created: {}", tokenProfile);
        tokenProfileRepository.save(tokenProfile);

        attributeService.createAttributeContent(tokenProfile.getUuid(), request.getCustomAttributes(), Resource.TOKEN_PROFILE);

        TokenProfileDetailDto tokenProfileDetailDto = tokenProfile.mapToDetailDto();
        tokenProfileDetailDto.setCustomAttributes(attributeService.getCustomAttributesWithValues(tokenProfile.getUuid(), Resource.TOKEN_PROFILE));
        logger.debug("Token profile detail: {}", tokenProfileDetailDto);
        return tokenProfileDetailDto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_PROFILE, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.TOKEN_PROFILE, action = ResourceAction.UPDATE, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public TokenProfileDetailDto editTokenProfile(SecuredParentUUID tokenInstanceUuid, SecuredUUID uuid, EditTokenProfileRequestDto request) throws ConnectorException {
        logger.info("Editing token profile with uuid: {}", uuid);
        TokenProfile tokenProfile = getTokenProfileEntity(uuid);

        TokenInstanceReference tokenInstanceReference = tokenInstanceReferenceRepository.findByUuid(tokenInstanceUuid)
                .orElseThrow(() -> new NotFoundException(TokenInstanceReference.class, tokenInstanceUuid));
        logger.debug("Token Instance Reference found: {}", tokenInstanceReference);
        attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.TOKEN_PROFILE);
        List<DataAttribute> attributes = mergeAndValidateAttributes(tokenInstanceReference, request.getAttributes());
        logger.debug("Attributes for the token profile: {}", attributes);
        tokenProfile = updateTokenProfile(tokenProfile, tokenInstanceReference, request, attributes);
        tokenProfileRepository.save(tokenProfile);

        attributeService.updateAttributeContent(tokenProfile.getUuid(), request.getCustomAttributes(), Resource.TOKEN_PROFILE);

        TokenProfileDetailDto tokenProfileDetailDto = tokenProfile.mapToDetailDto();
        tokenProfileDetailDto.setCustomAttributes(attributeService.getCustomAttributesWithValues(tokenProfile.getUuid(), Resource.TOKEN_PROFILE));
        logger.debug("Token profile detail: {}", tokenProfileDetailDto);
        return tokenProfileDetailDto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_PROFILE, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.TOKEN_PROFILE, action = ResourceAction.DELETE, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public void deleteTokenProfile(SecuredParentUUID tokenInstanceUuid, SecuredUUID uuid) throws NotFoundException {
        logger.info("Deleting token profile with uuid: {}", uuid);
        deleteProfileInternal(uuid, false);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_PROFILE, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.TOKEN_PROFILE, action = ResourceAction.DELETE)
    public void deleteTokenProfile(SecuredUUID tokenProfileUuid) throws NotFoundException {
        logger.info("Deleting token profile with uuid: {}", tokenProfileUuid);
        deleteProfileInternal(tokenProfileUuid, true);

    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_PROFILE, operation = OperationType.DISABLE)
    @ExternalAuthorization(resource = Resource.TOKEN_PROFILE, action = ResourceAction.ENABLE, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public void disableTokenProfile(SecuredParentUUID tokenInstanceUuid, SecuredUUID uuid) throws NotFoundException {
        logger.info("Disabling token profile with uuid: {}", uuid);
        disableProfileInternal(uuid);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_PROFILE, operation = OperationType.ENABLE)
    @ExternalAuthorization(resource = Resource.TOKEN_PROFILE, action = ResourceAction.ENABLE, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public void enableTokenProfile(SecuredParentUUID tokenInstanceUuid, SecuredUUID uuid) throws NotFoundException {
        logger.info("Enabling token profile with uuid: {}", uuid);
        enableProfileInternal(uuid);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_PROFILE, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.TOKEN_PROFILE, action = ResourceAction.DELETE, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public void deleteTokenProfile(List<SecuredUUID> uuids) {
        logger.info("Deleting token profiles with uuids: {}", uuids);
        for (SecuredUUID uuid : uuids) {
            try {
                deleteProfileInternal(uuid, false);
            } catch (NotFoundException e) {
                logger.warn("Unable to find Token Profile with uuid {}. It may have already been deleted", uuid);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_PROFILE, operation = OperationType.DISABLE)
    @ExternalAuthorization(resource = Resource.TOKEN_PROFILE, action = ResourceAction.ENABLE, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public void disableTokenProfile(List<SecuredUUID> uuids) {
        logger.info("Disabling token profiles with uuids: {}", uuids);
        for (SecuredUUID uuid : uuids) {
            try {
                disableProfileInternal(uuid);
            } catch (NotFoundException e) {
                logger.warn("Unable to find Token Profile with uuid {}. It may have already been deleted", uuid);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_PROFILE, operation = OperationType.ENABLE)
    @ExternalAuthorization(resource = Resource.TOKEN_PROFILE, action = ResourceAction.ENABLE, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public void enableTokenProfile(List<SecuredUUID> uuids) {
        logger.info("Enabling token profiles with uuids: {}", uuids);
        for (SecuredUUID uuid : uuids) {
            try {
                enableProfileInternal(uuid);
            } catch (NotFoundException e) {
                logger.warn("Unable to find Token Profile with uuid {}. It may have already been deleted", uuid);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_PROFILE, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.TOKEN_PROFILE, action = ResourceAction.UPDATE, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public void updateKeyUsages(List<SecuredUUID> uuids, List<KeyUsage> usages) {
        logger.info("Request to update the key usages for {} with usages {}", uuids, usages);
        // Iterate through the keys
        for (SecuredUUID uuid : uuids) {
            try {
                TokenProfile tokenProfile = getTokenProfileEntity(uuid);
                tokenProfile.setUsage(usages);
                tokenProfileRepository.save(tokenProfile);
            } catch (Exception e) {
                logger.warn(e.getMessage());
            }
        }
        logger.info("Key Usages Updated: {}", uuids);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.TOKEN_PROFILE, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.TOKEN_PROFILE, action = ResourceAction.UPDATE, parentResource = Resource.TOKEN, parentAction = ResourceAction.DETAIL)
    public void updateKeyUsages(SecuredParentUUID tokenInstanceUuid, SecuredUUID tokenProfileUuid, List<KeyUsage> usages) throws NotFoundException {
        TokenProfile tokenProfile = getTokenProfileEntity(tokenProfileUuid);
        tokenProfile.setUsage(usages);
        tokenProfileRepository.save(tokenProfile);
    }

    @Override
    @ExternalAuthorization(resource = Resource.TOKEN_PROFILE, action = ResourceAction.LIST)
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter) {
        logger.info("Listing token profiles with filter: {}", filter);
        return tokenProfileRepository.findUsingSecurityFilter(filter)
                .stream()
                .map(TokenProfile::mapToAccessControlObjects)
                .collect(Collectors.toList());
    }

    private List<DataAttribute> mergeAndValidateAttributes(TokenInstanceReference tokenInstanceRef, List<RequestAttributeDto> attributes) throws ConnectorException {
        logger.info("Merging and validating attributes for token instance: {}. Request Attributes: {}", tokenInstanceRef, attributes);
        List<BaseAttribute> definitions = tokenInstanceApiClient.listTokenProfileAttributes(
                tokenInstanceRef.getConnector().mapToDto(),
                tokenInstanceRef.getTokenInstanceUuid());
        logger.debug("Definitions from the connector: {}", definitions);
        List<String> existingAttributesFromConnector = definitions.stream().map(BaseAttribute::getName).collect(Collectors.toList());
        logger.debug("Existing attributes from connector: {}", existingAttributesFromConnector);
        for (RequestAttributeDto requestAttributeDto : attributes) {
            if (!existingAttributesFromConnector.contains(requestAttributeDto.getName())) {
                DataAttribute referencedAttribute = attributeService.getReferenceAttribute(tokenInstanceRef.getConnectorUuid(), requestAttributeDto.getName());
                if (referencedAttribute != null) {
                    definitions.add(referencedAttribute);
                }
            }
        }

        List<DataAttribute> merged = AttributeDefinitionUtils.mergeAttributes(definitions, attributes);
        logger.debug("Merged attributes: {}", merged);

        tokenInstanceApiClient.validateTokenProfileAttributes(
                tokenInstanceRef.getConnector().mapToDto(),
                tokenInstanceRef.getTokenInstanceUuid(),
                attributes);

        return merged;
    }

    private TokenProfile createTokenProfile(AddTokenProfileRequestDto request, List<DataAttribute> attributes, TokenInstanceReference tokenInstanceReference) {
        TokenProfile entity = new TokenProfile();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setAttributes(AttributeDefinitionUtils.serialize(attributes));
        entity.setEnabled(request.isEnabled());
        entity.setTokenInstanceName(tokenInstanceReference.getName());
        entity.setTokenInstanceReference(tokenInstanceReference);
        entity.setTokenInstanceReferenceUuid(tokenInstanceReference.getUuid());
        return entity;
    }

    private TokenProfile updateTokenProfile(TokenProfile entity, TokenInstanceReference tokenInstanceReference, EditTokenProfileRequestDto request, List<DataAttribute> attributes) {
        if (request.getDescription() != null) entity.setDescription(request.getDescription());
        entity.setAttributes(AttributeDefinitionUtils.serialize(attributes));
        entity.setTokenInstanceReference(tokenInstanceReference);
        if (request.getEnabled() != null) entity.setEnabled(request.getEnabled() != null && request.getEnabled());
        entity.setTokenInstanceName(tokenInstanceReference.getName());
        return entity;
    }

    //TODO - Check and delete the other associated objects
    private void deleteProfileInternal(SecuredUUID uuid, boolean throwWhenAssociated) throws NotFoundException {
        TokenProfile tokenProfile = getTokenProfileEntity(uuid);
        if (throwWhenAssociated && tokenProfile.getTokenInstanceReference() == null) {
            throw new ValidationException(ValidationError.create("Token Profile has associated Token Instance. Use other API"));
        }
        attributeService.deleteAttributeContent(tokenProfile.getUuid(), Resource.TOKEN_PROFILE);
        tokenProfileRepository.delete(tokenProfile);
    }

    private void disableProfileInternal(SecuredUUID uuid) throws NotFoundException {
        TokenProfile tokenProfile = getTokenProfileEntity(uuid);
        tokenProfile.setEnabled(false);
        tokenProfileRepository.save(tokenProfile);
    }

    private void enableProfileInternal(SecuredUUID uuid) throws NotFoundException {
        TokenProfile tokenProfile = getTokenProfileEntity(uuid);
        tokenProfile.setEnabled(true);
        tokenProfileRepository.save(tokenProfile);
    }

    @Override
    public TokenProfile getTokenProfileEntity(SecuredUUID uuid) throws NotFoundException {
        return tokenProfileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(TokenProfile.class, uuid));
    }
}
