package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.connector.ForceDeleteMessageDto;
import com.czertainly.api.model.client.credential.CredentialRequestDto;
import com.czertainly.api.model.client.credential.CredentialUpdateRequestDto;
import com.czertainly.api.model.common.attribute.AttributeCallback;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.common.attribute.RequestAttributeCallback;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.credential.CredentialDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Credential;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface CredentialService {
    List<CredentialDto> listCredentials(SecurityFilter filter);

    CredentialDto getCredential(SecuredUUID uuid) throws NotFoundException;

    Credential getCredentialEntity(SecuredUUID uuid) throws NotFoundException;

    CredentialDto createCredential(CredentialRequestDto request) throws AlreadyExistException, NotFoundException, ConnectorException;

    CredentialDto updateCredential(SecuredUUID uuid, CredentialUpdateRequestDto request) throws NotFoundException, ConnectorException;

    void removeCredential(SecuredUUID uuid) throws NotFoundException;

    void enableCredential(SecuredUUID uuid) throws NotFoundException;

    void disableCredential(SecuredUUID uuid) throws NotFoundException;

    List<ForceDeleteMessageDto> bulkRemoveCredential(List<SecuredUUID> uuids) throws ValidationException, NotFoundException;

    void bulkForceRemoveCredential(List<SecuredUUID> uuids) throws ValidationException, NotFoundException;

    void loadFullCredentialData(List<AttributeDefinition> attributes) throws NotFoundException;

    @AuditLogged(originator = ObjectType.BE, affected = ObjectType.CREDENTIAL, operation = OperationType.REQUEST)
    void loadFullCredentialData(AttributeCallback callback, RequestAttributeCallback callbackRequest) throws NotFoundException;
}
