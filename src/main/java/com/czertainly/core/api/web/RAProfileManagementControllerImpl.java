package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.RAProfileManagementController;
import com.czertainly.api.model.client.client.SimplifiedClientDto;
import com.czertainly.api.model.client.raprofile.ActivateAcmeForRaProfileRequestDto;
import com.czertainly.api.model.client.raprofile.AddRaProfileRequestDto;
import com.czertainly.api.model.client.raprofile.EditRaProfileRequestDto;
import com.czertainly.api.model.client.raprofile.RaProfileAcmeDetailResponseDto;
import com.czertainly.api.model.client.raprofile.RaProfileComplianceCheckDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.core.raprofile.RaProfileDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
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
        return raProfileService.listRaProfiles(SecurityFilter.create());
    }

    @Override
    public List<RaProfileDto> listRaProfiles(@RequestParam Boolean isEnabled) {
        return raProfileService.listRaProfiles(SecurityFilter.create(), isEnabled);
    }

    @Override
    public ResponseEntity<?> addRaProfile(@RequestBody AddRaProfileRequestDto request)
            throws AlreadyExistException, ValidationException, ConnectorException {
        RaProfileDto raProfile = raProfileService.addRaProfile(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}")
                .buildAndExpand(raProfile.getUuid()).toUri();
        UuidDto dto = new UuidDto();
        dto.setUuid(raProfile.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    public RaProfileDto getRaProfile(@PathVariable String uuid) throws NotFoundException {
        return raProfileService.getRaProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    public RaProfileDto editRaProfile(@PathVariable String uuid, @RequestBody EditRaProfileRequestDto request)
            throws NotFoundException, ConnectorException {
        return raProfileService.editRaProfile(SecuredUUID.fromString(uuid), request);
    }

    @Override
    public void removeRaProfile(@PathVariable String uuid) throws NotFoundException {
        raProfileService.removeRaProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    public void disableRaProfile(@PathVariable String uuid) throws NotFoundException {
        raProfileService.disableRaProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    public void enableRaProfile(@PathVariable String uuid) throws NotFoundException {
        raProfileService.enableRaProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    public List<SimplifiedClientDto> listClients(@PathVariable String uuid) throws NotFoundException {
        return raProfileService.listClients(SecuredUUID.fromString(uuid));
    }

    @Override
    public void bulkRemoveRaProfile(List<String> uuids) throws NotFoundException, ValidationException {
        raProfileService.bulkRemoveRaProfile(SecuredUUID.fromList(uuids));
    }

    @Override
    public void bulkDisableRaProfile(List<String> uuids) throws NotFoundException {
        raProfileService.bulkDisableRaProfile(SecuredUUID.fromList(uuids));
    }

    @Override
    public void bulkEnableRaProfile(List<String> uuids) throws NotFoundException {
        raProfileService.bulkEnableRaProfile(SecuredUUID.fromList(uuids));
    }

    @Override
    public RaProfileAcmeDetailResponseDto getAcmeForRaProfile(String uuid) throws NotFoundException {
        return raProfileService.getAcmeForRaProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    public RaProfileAcmeDetailResponseDto activateAcmeForRaProfile(String uuid, ActivateAcmeForRaProfileRequestDto request) throws ConnectorException, ValidationException {
        return raProfileService.activateAcmeForRaProfile(SecuredUUID.fromString(uuid), request);
    }

    @Override
    public void deactivateAcmeForRaProfile(String uuid) throws NotFoundException {
        raProfileService.deactivateAcmeForRaProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    public List<AttributeDefinition> listRevokeCertificateAttributes(String uuid) throws NotFoundException, ConnectorException {
        return raProfileService.listRevokeCertificateAttributes(SecuredUUID.fromString(uuid));
    }

    @Override
    public List<AttributeDefinition> listIssueCertificateAttributes(String uuid) throws NotFoundException, ConnectorException {
        return raProfileService.listIssueCertificateAttributes(SecuredUUID.fromString(uuid));
    }

    @Override
    public void checkCompliance(RaProfileComplianceCheckDto request) throws NotFoundException {
        raProfileService.checkCompliance(SecuredUUID.fromList(request.getRaProfileUuids()));
    }
}
