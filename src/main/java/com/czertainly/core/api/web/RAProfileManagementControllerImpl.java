package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.RAProfileManagementController;
import com.czertainly.api.model.client.raprofile.AddRaProfileRequestDto;
import com.czertainly.api.model.client.raprofile.EditRaProfileRequestDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.client.ClientDto;
import com.czertainly.api.model.core.raprofile.RaProfileDto;
import com.czertainly.core.service.RaProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class RAProfileManagementControllerImpl implements RAProfileManagementController {

    @Autowired
    private RaProfileService raProfileService;

    @Override
    public List<RaProfileDto> listRaProfiles() {
        return raProfileService.listRaProfiles();
    }

    @Override
    public List<RaProfileDto> listRaProfiles(@RequestParam Boolean isEnabled) {
        return raProfileService.listRaProfiles(isEnabled);
    }

    @Override
    public ResponseEntity<?> addRaProfile(@RequestBody AddRaProfileRequestDto request)
            throws AlreadyExistException, ValidationException, NotFoundException, ConnectorException {
        RaProfileDto raProfile = raProfileService.addRaProfile(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}")
                .buildAndExpand(raProfile.getUuid()).toUri();
        UuidDto dto = new UuidDto();
        dto.setUuid(raProfile.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    public RaProfileDto getRaProfile(@PathVariable String uuid) throws NotFoundException {
        return raProfileService.getRaProfile(uuid);
    }

    @Override
    public RaProfileDto editRaProfile(@PathVariable String uuid, @RequestBody EditRaProfileRequestDto request)
            throws NotFoundException, ConnectorException {
        return raProfileService.editRaProfile(uuid, request);
    }

    @Override
    public void removeRaProfile(@PathVariable String uuid) throws NotFoundException {
        raProfileService.removeRaProfile(uuid);
    }

    @Override
    public void disableRaProfile(@PathVariable String uuid) throws NotFoundException {
        raProfileService.disableRaProfile(uuid);
    }

    @Override
    public void enableRaProfile(@PathVariable String uuid) throws NotFoundException {
        raProfileService.enableRaProfile(uuid);
    }

    @Override
    public List<ClientDto> listClients(@PathVariable String uuid) throws NotFoundException {
        return raProfileService.listClients(uuid);
    }

    @Override
    public void bulkRemoveRaProfile(List<String> uuids) throws NotFoundException, ValidationException {
        raProfileService.bulkRemoveRaProfile(uuids);
    }

    @Override
    public void bulkDisableRaProfile(List<String> uuids) throws NotFoundException {
        raProfileService.bulkDisableRaProfile(uuids);
    }

    @Override
    public void bulkEnableRaProfile(List<String> uuids) throws NotFoundException {
        raProfileService.bulkEnableRaProfile(uuids);
    }
}
