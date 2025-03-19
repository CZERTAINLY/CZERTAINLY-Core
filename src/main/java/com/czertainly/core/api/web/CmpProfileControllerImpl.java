package com.czertainly.core.api.web;

import com.czertainly.api.exception.*;
import com.czertainly.api.interfaces.core.web.CmpProfileController;
import com.czertainly.api.model.client.cmp.CmpProfileEditRequestDto;
import com.czertainly.api.model.client.cmp.CmpProfileRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateDto;
import com.czertainly.api.model.core.cmp.CmpProfileDetailDto;
import com.czertainly.api.model.core.cmp.CmpProfileDto;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.CmpProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class CmpProfileControllerImpl implements CmpProfileController {

    // injectors

    private final CmpProfileService cmpProfileService;

    @Autowired
    public CmpProfileControllerImpl(CmpProfileService cmpProfileService) {
        this.cmpProfileService = cmpProfileService;
    }

    // methods

    @Override
    @AuthEndpoint(resourceName = Resource.CMP_PROFILE)
    @AuditLogged(module = Module.PROTOCOLS, resource = Resource.CMP_PROFILE, operation = Operation.LIST)
    public List<CmpProfileDto> listCmpProfiles() {
        return cmpProfileService.listCmpProfile(SecurityFilter.create());
    }

    @Override
    @AuditLogged(module = Module.PROTOCOLS, resource = Resource.CMP_PROFILE, operation = Operation.DETAIL)
    public CmpProfileDetailDto getCmpProfile(@LogResource(uuid = true) String cmpProfileUuid) throws NotFoundException {
        return cmpProfileService.getCmpProfile(SecuredUUID.fromString(cmpProfileUuid));
    }

    @Override
    @AuditLogged(module = Module.PROTOCOLS, resource = Resource.CMP_PROFILE, operation = Operation.CREATE)
    public ResponseEntity<CmpProfileDetailDto> createCmpProfile(CmpProfileRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException, AttributeException, NotFoundException {
        CmpProfileDetailDto cmpProfile = cmpProfileService.createCmpProfile(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}")
                .buildAndExpand(cmpProfile.getUuid()).toUri();
        return ResponseEntity.created(location).body(cmpProfile);
    }

    @Override
    @AuditLogged(module = Module.PROTOCOLS, resource = Resource.CMP_PROFILE, operation = Operation.UPDATE)
    public CmpProfileDetailDto editCmpProfile(@LogResource(uuid = true) String cmpProfileUuid, CmpProfileEditRequestDto request) throws ConnectorException, AttributeException, NotFoundException {
        return cmpProfileService.editCmpProfile(SecuredUUID.fromString(cmpProfileUuid), request);
    }

    @Override
    @AuditLogged(module = Module.PROTOCOLS, resource = Resource.CMP_PROFILE, operation = Operation.DELETE)
    public void deleteCmpProfile(@LogResource(uuid = true) String cmpProfileUuid) throws NotFoundException, ValidationException {
        cmpProfileService.deleteCmpProfile(SecuredUUID.fromString(cmpProfileUuid));
    }

    @Override
    @AuditLogged(module = Module.PROTOCOLS, resource = Resource.CMP_PROFILE, operation = Operation.DELETE)
    public List<BulkActionMessageDto> bulkDeleteCmpProfile(@LogResource(uuid = true) List<String> cmpProfileUuids) {
        return cmpProfileService.bulkDeleteCmpProfile(SecuredUUID.fromList(cmpProfileUuids));
    }

    @Override
    @AuditLogged(module = Module.PROTOCOLS, resource = Resource.CMP_PROFILE, operation = Operation.FORCE_DELETE)
    public List<BulkActionMessageDto> forceDeleteCmpProfiles(@LogResource(uuid = true) List<String> cmpProfileUuids) throws NotFoundException, ValidationException {
        return cmpProfileService.bulkForceRemoveCmpProfiles(SecuredUUID.fromList(cmpProfileUuids));
    }

    @Override
    @AuditLogged(module = Module.PROTOCOLS, resource = Resource.CMP_PROFILE, operation = Operation.ENABLE)
    public void enableCmpProfile(@LogResource(uuid = true) String cmpProfileUuid) throws NotFoundException {
        cmpProfileService.enableCmpProfile(SecuredUUID.fromString(cmpProfileUuid));
    }

    @Override
    @AuditLogged(module = Module.PROTOCOLS, resource = Resource.CMP_PROFILE, operation = Operation.ENABLE)
    public void bulkEnableCmpProfile(@LogResource(uuid = true) List<String> cmpProfileUuids) {
        cmpProfileService.bulkEnableCmpProfile(SecuredUUID.fromList(cmpProfileUuids));
    }

    @Override
    @AuditLogged(module = Module.PROTOCOLS, resource = Resource.CMP_PROFILE, operation = Operation.DISABLE)
    public void disableCmpProfile(@LogResource(uuid = true) String cmpProfileUuid) throws NotFoundException {
        cmpProfileService.disableCmpProfile(SecuredUUID.fromString(cmpProfileUuid));
    }

    @Override
    @AuditLogged(module = Module.PROTOCOLS, resource = Resource.CMP_PROFILE, operation = Operation.DISABLE)
    public void bulkDisableCmpProfile(@LogResource(uuid = true) List<String> cmpProfileUuids) {
        cmpProfileService.bulkDisableCmpProfile(SecuredUUID.fromList(cmpProfileUuids));
    }

    @Override
    @AuditLogged(module = Module.PROTOCOLS, resource = Resource.CMP_PROFILE, affiliatedResource = Resource.RA_PROFILE, operation = Operation.UPDATE_PROTOCOL_ISSUE_PROFILE)
    public void updateRaProfile(@LogResource(uuid = true) String cmpProfileUuid, @LogResource(uuid = true, affiliated = true) String raProfileUuid) throws NotFoundException {
        cmpProfileService.updateRaProfile(SecuredUUID.fromString(cmpProfileUuid), raProfileUuid);
    }

    @Override
    @AuditLogged(module = Module.PROTOCOLS, resource = Resource.CMP_PROFILE, affiliatedResource = Resource.CERTIFICATE, operation = Operation.LIST_PROTOCOL_CERTIFICATES)
    public List<CertificateDto> listCmpSigningCertificates() {
        return cmpProfileService.listCmpSigningCertificates();
    }

}
