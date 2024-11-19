package com.czertainly.core.api.web;

import com.czertainly.api.exception.*;
import com.czertainly.api.interfaces.core.web.CredentialController;
import com.czertainly.api.model.client.credential.CredentialRequestDto;
import com.czertainly.api.model.client.credential.CredentialUpdateRequestDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.credential.CredentialDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
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
    @AuthEndpoint(resourceName = Resource.CREDENTIAL)
    public List<CredentialDto> listCredentials() {
        return credentialService.listCredentials(SecurityFilter.create());
    }

    @Override
    public CredentialDto getCredential(@PathVariable String uuid) throws NotFoundException {
        return credentialService.getCredential(SecuredUUID.fromString(uuid));
    }

    @Override
    public ResponseEntity<?> createCredential(@RequestBody CredentialRequestDto request) throws AlreadyExistException, NotFoundException, ConnectorException, AttributeException {
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
    public CredentialDto editCredential(@PathVariable String uuid, @RequestBody CredentialUpdateRequestDto request) throws NotFoundException, ConnectorException, AttributeException {
        return credentialService.editCredential(SecuredUUID.fromString(uuid), request);
    }

    @Override
    public void deleteCredential(@PathVariable String uuid) throws NotFoundException {
        credentialService.deleteCredential(SecuredUUID.fromString(uuid));
    }

    @Override
    public void enableCredential(@PathVariable String uuid) throws NotFoundException {
        credentialService.enableCredential(SecuredUUID.fromString(uuid));
    }

    @Override
    public void disableCredential(@PathVariable String uuid) throws NotFoundException {
        credentialService.disableCredential(SecuredUUID.fromString(uuid));
    }

    @Override
    public void bulkDeleteCredential(List<String> uuids) throws NotFoundException, ValidationException {
        credentialService.bulkDeleteCredential(SecuredUUID.fromList(uuids));
    }
}
