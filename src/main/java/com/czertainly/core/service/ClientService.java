package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.client.AddClientRequestDto;
import com.czertainly.api.model.client.client.EditClientRequestDto;
import com.czertainly.api.model.client.connector.ForceDeleteMessageDto;
import com.czertainly.api.model.client.raprofile.SimplifiedRaProfileDto;
import com.czertainly.api.model.core.client.ClientDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.security.cert.CertificateException;
import java.util.List;

public interface ClientService {

    List<ClientDto> listClients(SecurityFilter filter);

    ClientDto addClient(AddClientRequestDto request)
            throws CertificateException, AlreadyExistException, NotFoundException, ValidationException;

    ClientDto getClient(SecuredUUID uuid) throws NotFoundException;

    ClientDto editClient(SecuredUUID uuid, EditClientRequestDto request) throws CertificateException, NotFoundException, AlreadyExistException;

    void removeClient(SecuredUUID uuid) throws NotFoundException;

    List<SimplifiedRaProfileDto> listAuthorizations(SecuredUUID uuid) throws NotFoundException;

    void authorizeClient(SecuredUUID uuid, String raProfileUuid) throws NotFoundException;

    void unauthorizeClient(SecuredUUID uuid, String raProfileUuid) throws NotFoundException;

    void enableClient(SecuredUUID uuid) throws NotFoundException, CertificateException;

    void disableClient(SecuredUUID uuid) throws NotFoundException;

    List<ForceDeleteMessageDto> bulkRemoveClient(List<SecuredUUID> clientUuids);

    void bulkDisableClient(List<SecuredUUID> clientUuids);

    void bulkEnableClient(List<SecuredUUID> clientUuids);
}
