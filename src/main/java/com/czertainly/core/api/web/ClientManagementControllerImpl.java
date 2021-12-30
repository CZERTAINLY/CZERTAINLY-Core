package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.ClientManagementController;
import com.czertainly.api.model.client.client.AddClientRequestDto;
import com.czertainly.api.model.client.client.EditClientRequestDto;
import com.czertainly.api.model.core.client.ClientDto;
import com.czertainly.api.model.core.raprofile.RaProfileDto;
import com.czertainly.core.service.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.security.cert.CertificateException;
import java.util.List;

@RestController
public class ClientManagementControllerImpl implements ClientManagementController {

    @Autowired
    private ClientService clientService;

    @Override
    public List<ClientDto> listClients() {
        return clientService.listClients();
    }

    @Override
    public ResponseEntity<?> addClient(
            @RequestBody AddClientRequestDto request)
            throws CertificateException, AlreadyExistException, NotFoundException, ValidationException {
        ClientDto client = clientService.addClient(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{clientUuid}")
                .buildAndExpand(client.getUuid())
                .toUri();

        return ResponseEntity.created(location).build();
    }

    @Override
    public ClientDto getClient(@PathVariable String uuid) throws NotFoundException {
        return clientService.getClient(uuid);
    }

    @Override
    public ClientDto editClient(
            @PathVariable String uuid,
            @RequestBody EditClientRequestDto request)
            throws CertificateException, NotFoundException, AlreadyExistException {
        return clientService.editClient(uuid, request);
    }

    @Override
    public void removeClient(
            @PathVariable String uuid)
            throws NotFoundException {
        clientService.removeClient(uuid);
    }

    @Override
    public void bulkRemoveClient(List<String> clientUuids) throws NotFoundException {
        clientService.bulkRemoveClient(clientUuids);
    }

    @Override
    public List<RaProfileDto> listAuthorizations(
            @PathVariable String uuid)
            throws NotFoundException {
        return clientService.listAuthorizations(uuid);
    }

    @Override
    public void disableClient(@PathVariable String uuid) throws NotFoundException {
        clientService.disableClient(uuid);
    }

    @Override
    public void enableClient(@PathVariable String uuid) throws NotFoundException, CertificateException {
        clientService.enableClient(uuid);
    }

    @Override
    public void bulkDisableClient(List<String> clientUuids) throws NotFoundException {
        clientService.bulkDisableClient(clientUuids);
    }

    @Override
    public void bulkEnableClient(List<String> clientUuids) throws NotFoundException {
        clientService.bulkEnableClient(clientUuids);
    }

    @Override
    public void authorizeClient(
            @PathVariable String uuid,
            @PathVariable String raProfileUuid)
            throws NotFoundException {
        clientService.authorizeClient(uuid, raProfileUuid);
    }

    @Override
    public void unauthorizeClient(
            @PathVariable String uuid,
            @PathVariable String raProfileUuid)
            throws NotFoundException {
        clientService.unauthorizeClient(uuid, raProfileUuid);
    }
}
