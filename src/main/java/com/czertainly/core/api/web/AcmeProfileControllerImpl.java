package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.AcmeProfileController;
import com.czertainly.api.model.client.acme.AcmeProfileEditRequestDto;
import com.czertainly.api.model.client.acme.AcmeProfileRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.acme.AcmeProfileDto;
import com.czertainly.api.model.core.acme.AcmeProfileListDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.ActionName;
import com.czertainly.core.model.auth.ResourceName;
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
    @AuthEndpoint(resourceName = ResourceName.ACME_PROFILE, actionName = ActionName.LIST, isListingEndPoint = true)
    public List<AcmeProfileListDto> listAcmeProfiles() {
        return acmeProfileService.listAcmeProfile();
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.ACME_PROFILE, actionName = ActionName.DETAIL)
    public AcmeProfileDto getAcmeProfile(String uuid) throws NotFoundException {
        return acmeProfileService.getAcmeProfile(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.ACME_PROFILE, actionName = ActionName.CREATE)
    public ResponseEntity<UuidDto> createAcmeProfile(AcmeProfileRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException {
        AcmeProfileDto acmeProfile = acmeProfileService.createAcmeProfile(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}")
                .buildAndExpand(acmeProfile.getUuid()).toUri();

        UuidDto dto = new UuidDto();
        dto.setUuid(acmeProfile.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.ACME_PROFILE, actionName = ActionName.UPDATE)
    public AcmeProfileDto editAcmeProfile(String uuid, AcmeProfileEditRequestDto request) throws ConnectorException {
        return acmeProfileService.editAcmeProfile(uuid, request);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.ACME_PROFILE, actionName = ActionName.DELETE)
    public void deleteAcmeProfile(String uuid) throws NotFoundException, ValidationException {
        acmeProfileService.deleteAcmeProfile(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.ACME_PROFILE, actionName = ActionName.ENABLE)
    public void enableAcmeProfile(String uuid) throws NotFoundException {
        acmeProfileService.enableAcmeProfile(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.ACME_PROFILE, actionName = ActionName.DISABLE)
    public void disableAcmeProfile(String uuid) throws NotFoundException {
        acmeProfileService.disableAcmeProfile(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.ACME_PROFILE, actionName = ActionName.ENABLE)
    public void bulkEnableAcmeProfile(List<String> uuids) {
        acmeProfileService.bulkEnableAcmeProfile(uuids);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.ACME_PROFILE, actionName = ActionName.DISABLE)
    public void bulkDisableAcmeProfile(List<String> uuids) {
        acmeProfileService.bulkDisableAcmeProfile(uuids);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.ACME_PROFILE, actionName = ActionName.DELETE)
    public List<BulkActionMessageDto> bulkDeleteAcmeProfile(List<String> uuids) {
        return acmeProfileService.bulkDeleteAcmeProfile(uuids);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.ACME_PROFILE, actionName = ActionName.FORCE_DELETE)
    public List<BulkActionMessageDto> forceDeleteACMEProfiles(List<String> uuids) throws NotFoundException, ValidationException {
        return acmeProfileService.bulkForceRemoveACMEProfiles(uuids);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.ACME_PROFILE, actionName = ActionName.UPDATE_RA_PROFILE)
    public void updateRaProfile(String uuid, String raProfileUuid) throws NotFoundException {
        acmeProfileService.updateRaProfile(uuid, raProfileUuid);
    }
}
