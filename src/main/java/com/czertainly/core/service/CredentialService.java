package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.connector.ForceDeleteMessageDto;
import com.czertainly.api.model.client.credential.CredentialRequestDto;
import com.czertainly.api.model.client.credential.CredentialUpdateRequestDto;
import com.czertainly.api.model.common.AttributeCallback;
import com.czertainly.api.model.common.AttributeDefinition;
import com.czertainly.api.model.common.RequestAttributeCallback;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.credential.CredentialDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Credential;

import java.util.List;

public interface CredentialService {
    List<CredentialDto> listCredentials();

    CredentialDto getCredential(String uuid) throws NotFoundException;

    Credential getCredentialEntity(Long id) throws NotFoundException;

    Credential getCredentialEntity(String uuid) throws NotFoundException;

    CredentialDto createCredential(CredentialRequestDto request) throws AlreadyExistException, NotFoundException, ConnectorException;

    CredentialDto updateCredential(String uuid, CredentialUpdateRequestDto request) throws NotFoundException, ConnectorException;

    void removeCredential(String uuid) throws NotFoundException;

    void enableCredential(String uuid) throws NotFoundException;

    void disableCredential(String uuid) throws NotFoundException;

    List<ForceDeleteMessageDto> bulkRemoveCredential(List<String> uuids) throws ValidationException, NotFoundException;

    void bulkForceRemoveCredential(List<String> uuids) throws ValidationException, NotFoundException;

    void loadFullCredentialData(List<AttributeDefinition> attributes) throws NotFoundException;

    @AuditLogged(originator = ObjectType.BE, affected = ObjectType.CREDENTIAL, operation = OperationType.REQUEST)
    void loadFullCredentialData(AttributeCallback callback, RequestAttributeCallback callbackRequest) throws NotFoundException;
}
