package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.AuthorityInstanceController;
import com.czertainly.api.model.client.authority.AuthorityInstanceRequestDto;
import com.czertainly.api.model.client.authority.AuthorityInstanceUpdateRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.NameAndIdDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.common.attribute.RequestAttributeDto;
import com.czertainly.api.model.core.authority.AuthorityInstanceDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.ActionName;
import com.czertainly.core.model.auth.ResourceName;
import com.czertainly.core.service.AuthorityInstanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class AuthorityInstanceControllerImpl implements AuthorityInstanceController {

    @Autowired
    private AuthorityInstanceService authorityInstanceService;

    @Override
    @AuthEndpoint(resourceName = ResourceName.AUTHORITY, actionName = ActionName.LIST, isListingEndPoint = true)
    public List<AuthorityInstanceDto> listAuthorityInstances() {
        return authorityInstanceService.listAuthorityInstances();
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.AUTHORITY, actionName = ActionName.DETAIL)
    public AuthorityInstanceDto getAuthorityInstance(@PathVariable String uuid) throws NotFoundException, ConnectorException {
        return authorityInstanceService.getAuthorityInstance(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.AUTHORITY, actionName = ActionName.CREATE)
    public ResponseEntity<?> createAuthorityInstance(@RequestBody AuthorityInstanceRequestDto request) throws AlreadyExistException, NotFoundException, ConnectorException {
        AuthorityInstanceDto authorityInstance = authorityInstanceService.createAuthorityInstance(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(authorityInstance.getUuid())
                .toUri();
        UuidDto dto = new UuidDto();
        dto.setUuid(authorityInstance.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.AUTHORITY, actionName = ActionName.UPDATE)
    public AuthorityInstanceDto editAuthorityInstance(@PathVariable String uuid, @RequestBody AuthorityInstanceUpdateRequestDto request) throws NotFoundException, ConnectorException {
        return authorityInstanceService.editAuthorityInstance(uuid, request);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.AUTHORITY, actionName = ActionName.DELETE)
    public void deleteAuthorityInstance(@PathVariable String uuid) throws NotFoundException, ConnectorException {
        authorityInstanceService.deleteAuthorityInstance(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.AUTHORITY, actionName = ActionName.LIST_ENTITY_PROFILE)
    public List<NameAndIdDto> listEntityProfiles(@PathVariable String uuid) throws NotFoundException, ConnectorException {
        return authorityInstanceService.listEndEntityProfiles(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.AUTHORITY, actionName = ActionName.LIST_CERTIFICATE_PROFILE)
    public List<NameAndIdDto> listCertificateProfiles(@PathVariable String uuid, @PathVariable Integer endEntityProfileId) throws NotFoundException, ConnectorException {
        return authorityInstanceService.listCertificateProfiles(uuid, endEntityProfileId);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.AUTHORITY, actionName = ActionName.LIST_CERTIFICATE_AUTHORITY)
    public List<NameAndIdDto> listCAsInProfile(@PathVariable String uuid, @PathVariable Integer endEntityProfileId) throws NotFoundException, ConnectorException {
        return authorityInstanceService.listCAsInProfile(uuid, endEntityProfileId);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.AUTHORITY, actionName = ActionName.LIST_ATTRIBUTES)
    public List<AttributeDefinition> listRAProfileAttributes(@PathVariable String uuid) throws NotFoundException, ConnectorException {
        return authorityInstanceService.listRAProfileAttributes(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.AUTHORITY, actionName = ActionName.VALIDATE_ATTRIBUTES)
    public void validateRAProfileAttributes(@PathVariable String uuid, @RequestBody List<RequestAttributeDto> attributes) throws NotFoundException, ConnectorException {
        authorityInstanceService.validateRAProfileAttributes(uuid, attributes);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.AUTHORITY, actionName = ActionName.DELETE)
    public List<BulkActionMessageDto> bulkDeleteAuthorityInstance(List<String> uuids) throws NotFoundException, ConnectorException, ValidationException {
        return authorityInstanceService.bulkDeleteAuthorityInstance(uuids);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.AUTHORITY, actionName = ActionName.FORCE_DELETE)
    public List<BulkActionMessageDto> forceDeleteAuthorityInstances(List<String> uuids) throws NotFoundException, ValidationException {
        return authorityInstanceService.forceDeleteAuthorityInstance(uuids);
    }
}
