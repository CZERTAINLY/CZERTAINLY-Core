package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.CredentialController;
import com.czertainly.api.model.client.credential.CredentialRequestDto;
import com.czertainly.api.model.client.credential.CredentialUpdateRequestDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.credential.CredentialDto;
import com.czertainly.core.service.CredentialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class CredentialControllerImpl implements CredentialController {

    @Autowired
    private CredentialService credentialService;

    @Override
    public List<CredentialDto> listCredentials() {
        return credentialService.listCredentials();
    }

    @Override
    public CredentialDto getCredential(@PathVariable String uuid) throws NotFoundException {
        return credentialService.getCredential(uuid);
    }

    @Override
    public ResponseEntity<?> createCredential(@RequestBody CredentialRequestDto request) throws AlreadyExistException, NotFoundException, ConnectorException {
        CredentialDto credentialDto = credentialService.createCredential(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(credentialDto.getUuid())
                .toUri();

        UuidDto dto = new UuidDto();
        dto.setUuid(credentialDto.getUuid());

        return ResponseEntity.created(location).body(dto);
    }

    @Override
    public CredentialDto updateCredential(@PathVariable String uuid, @RequestBody CredentialUpdateRequestDto request) throws NotFoundException, ConnectorException {
        return credentialService.updateCredential(uuid, request);
    }

    @Override
    public void removeCredential(@PathVariable String uuid) throws NotFoundException {
        credentialService.removeCredential(uuid);
    }

    @Override
    public void enableCredential(@PathVariable String uuid) throws NotFoundException {
        credentialService.enableCredential(uuid);
    }

    @Override
    public void disableCredential(@PathVariable String uuid) throws NotFoundException {
        credentialService.disableCredential(uuid);
    }

    @Override
    public void bulkRemoveCredential(List<String> uuids) throws NotFoundException, ValidationException {
        credentialService.bulkRemoveCredential(uuids);
    }

    @Override
    public void bulkForceRemoveCredential(List<String> uuids) throws NotFoundException, ValidationException {
        credentialService.bulkForceRemoveCredential(uuids);
    }
}
