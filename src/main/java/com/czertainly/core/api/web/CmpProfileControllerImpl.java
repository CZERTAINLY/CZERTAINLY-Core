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
import com.czertainly.core.auth.AuthEndpoint;
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
    public List<CmpProfileDto> listCmpProfiles() {
        return cmpProfileService.listCmpProfile(SecurityFilter.create());
    }

    @Override
    public CmpProfileDetailDto getCmpProfile(String cmpProfileUuid) throws NotFoundException {
        return cmpProfileService.getCmpProfile(SecuredUUID.fromString(cmpProfileUuid));
    }

    @Override
    public ResponseEntity<CmpProfileDetailDto> createCmpProfile(CmpProfileRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException, AttributeException {
        CmpProfileDetailDto cmpProfile = cmpProfileService.createCmpProfile(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}")
                .buildAndExpand(cmpProfile.getUuid()).toUri();
        return ResponseEntity.created(location).body(cmpProfile);
    }

    @Override
    public CmpProfileDetailDto editCmpProfile(String cmpProfileUuid, CmpProfileEditRequestDto request) throws ConnectorException, AttributeException {
        return cmpProfileService.editCmpProfile(SecuredUUID.fromString(cmpProfileUuid), request);
    }

    @Override
    public void deleteCmpProfile(String cmpProfileUuid) throws NotFoundException, ValidationException {
        cmpProfileService.deleteCmpProfile(SecuredUUID.fromString(cmpProfileUuid));
    }

    @Override
    public List<BulkActionMessageDto> bulkDeleteCmpProfile(List<String> cmpProfileUuids) {
        return cmpProfileService.bulkDeleteCmpProfile(SecuredUUID.fromList(cmpProfileUuids));
    }

    @Override
    public List<BulkActionMessageDto> forceDeleteCmpProfiles(List<String> cmpProfileUuids) throws NotFoundException, ValidationException {
        return cmpProfileService.bulkForceRemoveCmpProfiles(SecuredUUID.fromList(cmpProfileUuids));
    }

    @Override
    public void enableCmpProfile(String cmpProfileUuid) throws NotFoundException {
        cmpProfileService.enableCmpProfile(SecuredUUID.fromString(cmpProfileUuid));
    }

    @Override
    public void bulkEnableCmpProfile(List<String> cmpProfileUuids) {
        cmpProfileService.bulkEnableCmpProfile(SecuredUUID.fromList(cmpProfileUuids));
    }

    @Override
    public void disableCmpProfile(String cmpProfileUuid) throws NotFoundException {
        cmpProfileService.disableCmpProfile(SecuredUUID.fromString(cmpProfileUuid));
    }

    @Override
    public void bulkDisableCmpProfile(List<String> cmpProfileUuids) {
        cmpProfileService.bulkDisableCmpProfile(SecuredUUID.fromList(cmpProfileUuids));
    }

    @Override
    public void updateRaProfile(String cmpProfileUuid, String raProfileUuid) throws NotFoundException {
        cmpProfileService.updateRaProfile(SecuredUUID.fromString(cmpProfileUuid), raProfileUuid);
    }

    @Override
    public List<CertificateDto> listCmpSigningCertificates() {
        return cmpProfileService.listCmpSigningCertificates();
    }

}
