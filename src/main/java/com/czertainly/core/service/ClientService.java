package com.czertainly.core.service;

import com.czertainly.api.core.modal.AddClientRequestDto;
import com.czertainly.api.core.modal.ClientDto;
import com.czertainly.api.core.modal.EditClientRequestDto;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.raprofile.RaProfileDto;

import java.security.cert.CertificateException;
import java.util.List;

public interface ClientService {

    List<ClientDto> listClients();

    ClientDto addClient(AddClientRequestDto request)
            throws CertificateException, AlreadyExistException, NotFoundException, ValidationException;

    ClientDto getClient(String uuid) throws NotFoundException;

    ClientDto getClientBySerialNumber(String serialNumber) throws NotFoundException;

    ClientDto editClient(String uuid, EditClientRequestDto request) throws CertificateException, NotFoundException, AlreadyExistException;

    void removeClient(String uuid) throws NotFoundException;

    List<RaProfileDto> listAuthorizations(String uuid) throws NotFoundException;

    void authorizeClient(String uuid, String raProfileUuid) throws NotFoundException;

    void unauthorizeClient(String uuid, String raProfileUuid) throws NotFoundException;

    void enableClient(String uuid) throws NotFoundException, CertificateException;

    void disableClient(String uuid) throws NotFoundException;

    void bulkRemoveClient(List<String> clientUuids);

    void bulkDisableClient(List<String> clientUuids);

    void bulkEnableClient(List<String> clientUuids);
}
