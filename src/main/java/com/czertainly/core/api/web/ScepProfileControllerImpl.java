package com.czertainly.core.api.web;

import com.czertainly.api.exception.*;
import com.czertainly.api.interfaces.core.web.ScepProfileController;
import com.czertainly.api.model.client.scep.ScepProfileEditRequestDto;
import com.czertainly.api.model.client.scep.ScepProfileRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.core.certificate.CertificateDto;
import com.czertainly.api.model.core.scep.ScepProfileDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.scep.ScepProfileDetailDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.ScepProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class ScepProfileControllerImpl implements ScepProfileController {

    @Autowired
    private ScepProfileService scepProfileService;

    @Override
    @AuthEndpoint(resourceName = Resource.SCEP_PROFILE)
    public List<ScepProfileDto> listScepProfiles() {
        return scepProfileService.listScepProfile(SecurityFilter.create());
    }

    @Override
    public ScepProfileDetailDto getScepProfile(String uuid) throws NotFoundException {
        return scepProfileService.getScepProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    public ResponseEntity<ScepProfileDetailDto> createScepProfile(ScepProfileRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException, AttributeException {
        ScepProfileDetailDto scepProfile = scepProfileService.createScepProfile(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}")
                .buildAndExpand(scepProfile.getUuid()).toUri();
        return ResponseEntity.created(location).body(scepProfile);
    }

    @Override
    public ScepProfileDetailDto editScepProfile(String uuid, ScepProfileEditRequestDto request) throws ConnectorException, AttributeException {
        return scepProfileService.editScepProfile(SecuredUUID.fromString(uuid), request);
    }

    @Override
    public void deleteScepProfile(String uuid) throws NotFoundException, ValidationException {
        scepProfileService.deleteScepProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    public List<BulkActionMessageDto> bulkDeleteScepProfile(List<String> uuids) {
        return scepProfileService.bulkDeleteScepProfile(SecuredUUID.fromList(uuids));
    }

    @Override
    public List<BulkActionMessageDto> forceDeleteScepProfiles(List<String> uuids) throws NotFoundException, ValidationException {
        return scepProfileService.bulkForceRemoveScepProfiles(SecuredUUID.fromList(uuids));
    }

    @Override
    public void enableScepProfile(String uuid) throws NotFoundException {
        scepProfileService.enableScepProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    public void bulkEnableScepProfile(List<String> uuids) {
        scepProfileService.bulkEnableScepProfile(SecuredUUID.fromList(uuids));
    }

    @Override
    public void disableScepProfile(String uuid) throws NotFoundException {
        scepProfileService.disableScepProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    public void bulkDisableScepProfile(List<String> uuids) {
        scepProfileService.bulkDisableScepProfile(SecuredUUID.fromList(uuids));
    }

    @Override
    public void updateRaProfile(String uuid, String raProfileUuid) throws NotFoundException {
        scepProfileService.updateRaProfile(SecuredUUID.fromString(uuid), raProfileUuid);
    }

    @Override
    public List<CertificateDto> listScepCaCertificates(boolean intuneEnabled) {
        return scepProfileService.listScepCaCertificates(intuneEnabled);
    }
}
