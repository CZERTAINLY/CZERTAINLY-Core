package com.czertainly.core.api.web;

import com.czertainly.api.exception.*;
import com.czertainly.api.interfaces.core.web.AcmeProfileController;
import com.czertainly.api.model.client.acme.AcmeProfileEditRequestDto;
import com.czertainly.api.model.client.acme.AcmeProfileRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.acme.AcmeProfileDto;
import com.czertainly.api.model.core.acme.AcmeProfileListDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AcmeProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class AcmeProfileControllerImpl implements AcmeProfileController {

    @Autowired
    private AcmeProfileService acmeProfileService;

    @Override
    @AuthEndpoint(resourceName = Resource.ACME_PROFILE)
    public List<AcmeProfileListDto> listAcmeProfiles() {
        return acmeProfileService.listAcmeProfile(SecurityFilter.create());
    }

    @Override
    public AcmeProfileDto getAcmeProfile(String uuid) throws NotFoundException {
        return acmeProfileService.getAcmeProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    public ResponseEntity<UuidDto> createAcmeProfile(AcmeProfileRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException, AttributeException {
        AcmeProfileDto acmeProfile = acmeProfileService.createAcmeProfile(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}")
                .buildAndExpand(acmeProfile.getUuid()).toUri();

        UuidDto dto = new UuidDto();
        dto.setUuid(acmeProfile.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    public AcmeProfileDto editAcmeProfile(String uuid, AcmeProfileEditRequestDto request) throws ConnectorException, AttributeException {
        return acmeProfileService.editAcmeProfile(SecuredUUID.fromString(uuid), request);
    }

    @Override
    public void deleteAcmeProfile(String uuid) throws NotFoundException, ValidationException {
        acmeProfileService.deleteAcmeProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    public void enableAcmeProfile(String uuid) throws NotFoundException {
        acmeProfileService.enableAcmeProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    public void disableAcmeProfile(String uuid) throws NotFoundException {
        acmeProfileService.disableAcmeProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    public void bulkEnableAcmeProfile(List<String> uuids) {
        acmeProfileService.bulkEnableAcmeProfile(SecuredUUID.fromList(uuids));
    }

    @Override
    public void bulkDisableAcmeProfile(List<String> uuids) {
        acmeProfileService.bulkDisableAcmeProfile(SecuredUUID.fromList(uuids));
    }

    @Override
    public List<BulkActionMessageDto> bulkDeleteAcmeProfile(List<String> uuids) {
        return acmeProfileService.bulkDeleteAcmeProfile(SecuredUUID.fromList(uuids));
    }

    @Override
    public List<BulkActionMessageDto> forceDeleteACMEProfiles(List<String> uuids) throws NotFoundException, ValidationException {
        return acmeProfileService.bulkForceRemoveACMEProfiles(SecuredUUID.fromList(uuids));
    }

    @Override
    public void updateRaProfile(String uuid, String raProfileUuid) throws NotFoundException {
        acmeProfileService.updateRaProfile(SecuredUUID.fromString(uuid), raProfileUuid);
    }
}
