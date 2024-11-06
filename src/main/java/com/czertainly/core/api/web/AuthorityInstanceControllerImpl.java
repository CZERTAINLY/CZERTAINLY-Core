package com.czertainly.core.api.web;

import com.czertainly.api.exception.*;
import com.czertainly.api.interfaces.core.web.AuthorityInstanceController;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.authority.AuthorityInstanceRequestDto;
import com.czertainly.api.model.client.authority.AuthorityInstanceUpdateRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.NameAndIdDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.authority.AuthorityInstanceDto;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
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

    private AuthorityInstanceService authorityInstanceService;

    @Override
    @AuthEndpoint(resourceName = Resource.AUTHORITY)
    @AuditLogged(module = Module.CERTIFICATES, resource = Resource.AUTHORITY, operation = Operation.LIST)
    public List<AuthorityInstanceDto> listAuthorityInstances() {
        return authorityInstanceService.listAuthorityInstances(SecurityFilter.create());
    }

    @Override
    @AuditLogged(module = Module.CERTIFICATES, resource = Resource.AUTHORITY, operation = Operation.DETAIL)
    public AuthorityInstanceDto getAuthorityInstance(@LogResource(uuid = true) @PathVariable String uuid) throws NotFoundException, ConnectorException {
        return authorityInstanceService.getAuthorityInstance(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuditLogged(module = Module.CERTIFICATES, resource = Resource.AUTHORITY, operation = Operation.CREATE)
    public ResponseEntity<?> createAuthorityInstance(@RequestBody AuthorityInstanceRequestDto request) throws AlreadyExistException, NotFoundException, ConnectorException, AttributeException {
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
    @AuditLogged(module = Module.CERTIFICATES, resource = Resource.AUTHORITY, operation = Operation.UPDATE)
    public AuthorityInstanceDto editAuthorityInstance(@LogResource(uuid = true) @PathVariable String uuid, @RequestBody AuthorityInstanceUpdateRequestDto request) throws NotFoundException, ConnectorException, AttributeException {
        return authorityInstanceService.editAuthorityInstance(SecuredUUID.fromString(uuid), request);
    }

    @Override
    @AuditLogged(module = Module.CERTIFICATES, resource = Resource.AUTHORITY, operation = Operation.DELETE)
    public void deleteAuthorityInstance(@LogResource(uuid = true) @PathVariable String uuid) throws NotFoundException, ConnectorException {
        authorityInstanceService.deleteAuthorityInstance(SecuredUUID.fromString(uuid));
    }

    @Override
    public List<NameAndIdDto> listEntityProfiles(@PathVariable String uuid) throws NotFoundException, ConnectorException {
        return authorityInstanceService.listEndEntityProfiles(SecuredUUID.fromString(uuid));
    }

    @Override
    public List<NameAndIdDto> listCertificateProfiles(@PathVariable String uuid, @PathVariable Integer endEntityProfileId) throws NotFoundException, ConnectorException {
        return authorityInstanceService.listCertificateProfiles(SecuredUUID.fromString(uuid), endEntityProfileId);
    }

    @Override
    public List<NameAndIdDto> listCAsInProfile(@PathVariable String uuid, @PathVariable Integer endEntityProfileId) throws NotFoundException, ConnectorException {
        return authorityInstanceService.listCAsInProfile(SecuredUUID.fromString(uuid), endEntityProfileId);
    }

    @Override
    public List<BaseAttribute> listRAProfileAttributes(@PathVariable String uuid) throws NotFoundException, ConnectorException {
        return authorityInstanceService.listRAProfileAttributes(SecuredUUID.fromString(uuid));
    }

    @Override
    public void validateRAProfileAttributes(@PathVariable String uuid, @RequestBody List<RequestAttributeDto> attributes) throws NotFoundException, ConnectorException {
        authorityInstanceService.validateRAProfileAttributes(SecuredUUID.fromString(uuid), attributes);
    }

    @Override
    @AuditLogged(module = Module.CERTIFICATES, resource = Resource.AUTHORITY, operation = Operation.DELETE)
    public List<BulkActionMessageDto> bulkDeleteAuthorityInstance(@LogResource(uuid = true) List<String> uuids) throws NotFoundException, ConnectorException, ValidationException {
        return authorityInstanceService.bulkDeleteAuthorityInstance(SecuredUUID.fromList(uuids));
    }

    @Override
    @AuditLogged(module = Module.CERTIFICATES, resource = Resource.AUTHORITY, operation = Operation.FORCE_DELETE)
    public List<BulkActionMessageDto> forceDeleteAuthorityInstances(@LogResource(uuid = true) List<String> uuids) throws NotFoundException, ValidationException {
        return authorityInstanceService.forceDeleteAuthorityInstance(SecuredUUID.fromList(uuids));
    }

    // SETTERs

    @Autowired
    public void setAuthorityInstanceService(AuthorityInstanceService authorityInstanceService) {
        this.authorityInstanceService = authorityInstanceService;
    }

}
