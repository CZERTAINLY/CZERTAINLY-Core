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
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.service.RaProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@RestController
public class RAProfileManagementControllerImpl implements RAProfileManagementController {

    @Autowired
    private RaProfileService raProfileService;

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.LIST, isListingEndPoint = true)
    public List<RaProfileDto> listRaProfiles(Optional<Boolean> enabled) {
        return raProfileService.listRaProfiles(enabled);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.CREATE)
    public ResponseEntity<?> createRaProfile(@RequestBody AddRaProfileRequestDto request)
            throws AlreadyExistException, ValidationException, NotFoundException, ConnectorException {
        RaProfileDto raProfile = raProfileService.addRaProfile(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}")
                .buildAndExpand(raProfile.getUuid()).toUri();
        UuidDto dto = new UuidDto();
        dto.setUuid(raProfile.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.DETAIL)
    public RaProfileDto getRaProfile(@PathVariable String uuid) throws NotFoundException {
        return raProfileService.getRaProfile(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.UPDATE)
    public RaProfileDto editRaProfile(@PathVariable String uuid, @RequestBody EditRaProfileRequestDto request)
            throws NotFoundException, ConnectorException {
        return raProfileService.editRaProfile(uuid, request);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.DELETE)
    public void deleteRaProfile(@PathVariable String uuid) throws NotFoundException {
        raProfileService.deleteRaProfile(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.DISABLE)
    public void disableRaProfile(@PathVariable String uuid) throws NotFoundException {
        raProfileService.disableRaProfile(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.ENABLE)
    public void enableRaProfile(@PathVariable String uuid) throws NotFoundException {
        raProfileService.enableRaProfile(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.LIST_AUTHORIZATIONS)
    public List<SimplifiedClientDto> listClients(@PathVariable String uuid) throws NotFoundException {
        return raProfileService.listClients(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.DELETE)
    public void bulkDeleteRaProfile(List<String> uuids) throws NotFoundException, ValidationException {
        raProfileService.bulkDeleteRaProfile(uuids);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.DISABLE)
    public void bulkDisableRaProfile(List<String> uuids) throws NotFoundException {
        raProfileService.bulkDisableRaProfile(uuids);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.ENABLE)
    public void bulkEnableRaProfile(List<String> uuids) throws NotFoundException {
        raProfileService.bulkEnableRaProfile(uuids);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.ACME_DETAIL)
    public RaProfileAcmeDetailResponseDto getAcmeForRaProfile(String uuid) throws NotFoundException {
        return raProfileService.getAcmeForRaProfile(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.ACTIVATE_ACME)
    public RaProfileAcmeDetailResponseDto activateAcmeForRaProfile(String uuid, ActivateAcmeForRaProfileRequestDto request) throws ConnectorException, ValidationException {
        return raProfileService.activateAcmeForRaProfile(uuid, request);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.ACTIVATE_ACME)
    public void deactivateAcmeForRaProfile(String uuid) throws NotFoundException {
        raProfileService.deactivateAcmeForRaProfile(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.NONE)
    public List<AttributeDefinition> listRevokeCertificateAttributes(String uuid) throws NotFoundException, ConnectorException {
        return raProfileService.listRevokeCertificateAttributes(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.NONE)
    public List<AttributeDefinition> listIssueCertificateAttributes(String uuid) throws NotFoundException, ConnectorException {
        return raProfileService.listIssueCertificateAttributes(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.CHECK_COMPLIANCE)
    public void checkCompliance(RaProfileComplianceCheckDto request) throws NotFoundException {
        raProfileService.checkCompliance(request);
    }
}
