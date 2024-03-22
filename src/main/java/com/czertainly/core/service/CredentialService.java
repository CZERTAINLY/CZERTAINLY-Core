package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.credential.CredentialRequestDto;
import com.czertainly.api.model.client.credential.CredentialUpdateRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.callback.AttributeCallback;
import com.czertainly.api.model.common.attribute.v2.callback.RequestAttributeCallback;
import com.czertainly.api.model.core.credential.CredentialDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface CredentialService extends ResourceExtensionService {
    List<CredentialDto> listCredentials(SecurityFilter filter);

    List<NameAndUuidDto> listCredentialsCallback(SecurityFilter filter, String kind) throws NotFoundException;

    CredentialDto getCredential(SecuredUUID uuid) throws NotFoundException;

    CredentialDto createCredential(CredentialRequestDto request) throws AlreadyExistException, ConnectorException, AttributeException;

    CredentialDto editCredential(SecuredUUID uuid, CredentialUpdateRequestDto request) throws ConnectorException, AttributeException;

    void deleteCredential(SecuredUUID uuid) throws NotFoundException;

    void enableCredential(SecuredUUID uuid) throws NotFoundException;

    void disableCredential(SecuredUUID uuid) throws NotFoundException;

    void bulkDeleteCredential(List<SecuredUUID> uuids) throws ValidationException, NotFoundException;

    void loadFullCredentialData(List<DataAttribute> attributes) throws NotFoundException;

    void loadFullCredentialData(AttributeCallback callback, RequestAttributeCallback callbackRequest) throws NotFoundException;
}
