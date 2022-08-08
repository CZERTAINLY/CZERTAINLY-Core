package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.ClientManagementController;
import com.czertainly.api.model.client.client.AddClientRequestDto;
import com.czertainly.api.model.client.client.EditClientRequestDto;
import com.czertainly.api.model.client.raprofile.SimplifiedRaProfileDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.client.ClientDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.service.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
    @AuthEndpoint(resourceName = Resource.CLIENT, actionName = ResourceAction.LIST, isListingEndPoint = true)
    public List<ClientDto> listClients() {
        return clientService.listClients();
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CLIENT, actionName = ResourceAction.CREATE)
    public ResponseEntity<UuidDto> createClient(
            @RequestBody AddClientRequestDto request)
            throws CertificateException, AlreadyExistException, NotFoundException, ValidationException {
        ClientDto client = clientService.addClient(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{clientUuid}")
                .buildAndExpand(client.getUuid())
                .toUri();

        UuidDto dto = new UuidDto();
        dto.setUuid(client.getUuid());

        return ResponseEntity.created(location).body(dto);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CLIENT, actionName = ResourceAction.DETAIL)
    public ClientDto getClient(@PathVariable String uuid) throws NotFoundException {
        return clientService.getClient(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CLIENT, actionName = ResourceAction.UPDATE)
    public ClientDto editClient(
            @PathVariable String uuid,
            @RequestBody EditClientRequestDto request)
            throws CertificateException, NotFoundException, AlreadyExistException {
        return clientService.editClient(uuid, request);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CLIENT, actionName = ResourceAction.DELETE)
    public void deleteClient(
            @PathVariable String uuid)
            throws NotFoundException {
        clientService.deleteClient(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CLIENT, actionName = ResourceAction.DELETE)
    public ResponseEntity<List<BulkActionMessageDto>> bulkDeleteClient(List<String> clientUuids) throws NotFoundException {
        List<BulkActionMessageDto> messages = clientService.bulkDeleteClient(clientUuids);
        if(messages.isEmpty()){
            return ResponseEntity.ok().body(messages);
        }
        return ResponseEntity.status(HttpStatus.valueOf(422)).body(messages);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CLIENT, actionName = ResourceAction.LIST_AUTHORIZATIONS, isListingEndPoint = true)
    public List<SimplifiedRaProfileDto> listAuthorizations(
            @PathVariable String uuid)
            throws NotFoundException {
        return clientService.listAuthorizations(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CLIENT, actionName = ResourceAction.DISABLE)
    public void disableClient(@PathVariable String uuid) throws NotFoundException {
        clientService.disableClient(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CLIENT, actionName = ResourceAction.ENABLE)
    public void enableClient(@PathVariable String uuid) throws NotFoundException, CertificateException {
        clientService.enableClient(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CLIENT, actionName = ResourceAction.DISABLE)
    public void bulkDisableClient(List<String> clientUuids) throws NotFoundException {
        clientService.bulkDisableClient(clientUuids);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CLIENT, actionName = ResourceAction.ENABLE)
    public void bulkEnableClient(List<String> clientUuids) throws NotFoundException {
        clientService.bulkEnableClient(clientUuids);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CLIENT, actionName = ResourceAction.AUTHORIZE_CLIENT)
    public void authorizeClient(
            @PathVariable String uuid,
            @PathVariable String raProfileUuid)
            throws NotFoundException {
        clientService.authorizeClient(uuid, raProfileUuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CLIENT, actionName = ResourceAction.UNAUTHORIZE_CLIENT)
    public void unauthorizeClient(
            @PathVariable String uuid,
            @PathVariable String raProfileUuid)
            throws NotFoundException {
        clientService.unauthorizeClient(uuid, raProfileUuid);
    }
}
