package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.AcmeProfileController;
import com.czertainly.api.model.client.acme.AcmeProfileRequestDto;
import com.czertainly.api.model.client.connector.ForceDeleteMessageDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.acme.AcmeProfileDto;
import com.czertainly.api.model.core.acme.AcmeProfileListDto;
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
    public List<AcmeProfileListDto> listAcmeProfile() {
        return acmeProfileService.listAcmeProfile();
    }

    @Override
    public AcmeProfileDto getAcmeProfile(String uuid) throws NotFoundException {
        return acmeProfileService.getAcmeProfile(uuid);
    }

    @Override
    public ResponseEntity<UuidDto> createAcmeProfile(AcmeProfileRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException {
        AcmeProfileDto acmeProfile = acmeProfileService.createAcmeProfile(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}")
                .buildAndExpand(acmeProfile.getUuid()).toUri();

        UuidDto dto = new UuidDto();
        dto.setUuid(acmeProfile.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    public AcmeProfileDto updateAcmeProfile(String uuid, AcmeProfileRequestDto request) throws ConnectorException {
        return acmeProfileService.updateAcmeProfile(uuid, request);
    }

    @Override
    public List<ForceDeleteMessageDto> deleteAcmeProfile(String uuid) throws NotFoundException {
        return acmeProfileService.deleteAcmeProfile(uuid);
    }

    @Override
    public void enableAcmeProfile(String uuid) throws NotFoundException {
        acmeProfileService.enableAcmeProfile(uuid);
    }

    @Override
    public void disableAcmeProfile(String uuid) throws NotFoundException {
        acmeProfileService.disableAcmeProfile(uuid);
    }

    @Override
    public void bulkEnableAcmeProfile(List<String> uuids) {
        acmeProfileService.bulkEnableAcmeProfile(uuids);
    }

    @Override
    public void bulkDisableAcmeProfile(List<String> uuids) {
        acmeProfileService.bulkDisableAcmeProfile(uuids);
    }

    @Override
    public List<ForceDeleteMessageDto> bulkDeleteAcmeProfile(List<String> uuids) {
        return acmeProfileService.bulkDeleteAcmeProfile(uuids);
    }

    @Override
    public void bulkForceRemoveACMEProfiles(List<String> uuids) throws NotFoundException, ValidationException {
        acmeProfileService.bulkForceRemoveACMEProfiles(uuids);
    }

    @Override
    public void updateRaProfile(String uuid, String raProfileUuid) throws NotFoundException {
        acmeProfileService.updateRaProfile(uuid, raProfileUuid);
    }
}
