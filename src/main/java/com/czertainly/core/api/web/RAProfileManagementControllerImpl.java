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
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.core.raprofile.RaProfileDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.RaProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
        return raProfileService.listRaProfiles(SecurityFilter.create(), enabled);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.CREATE)
    public ResponseEntity<?> createRaProfile(String authorityUuid, AddRaProfileRequestDto request)
            throws AlreadyExistException, ValidationException, ConnectorException {
        RaProfileDto raProfile = raProfileService.addRaProfile(SecuredUUID.fromString(authorityUuid), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}")
                .buildAndExpand(raProfile.getUuid()).toUri();
        UuidDto dto = new UuidDto();
        dto.setUuid(raProfile.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.DETAIL)
    public RaProfileDto getRaProfile(String authorityUuid, String raProfileUuid) throws NotFoundException {
        return raProfileService.getRaProfile(SecuredUUID.fromString(raProfileUuid));
    }

    @Override
    public RaProfileDto getRaProfile(String raProfileUuid) throws NotFoundException {
        return raProfileService.getRaProfile(SecuredUUID.fromString(raProfileUuid));
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.UPDATE)
    public RaProfileDto editRaProfile(String authorityUuid, String raProfileUuid, EditRaProfileRequestDto request)
            throws ConnectorException {
        return raProfileService.editRaProfile(SecuredUUID.fromString(authorityUuid), SecuredUUID.fromString(raProfileUuid), request);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.DELETE)
    public void deleteRaProfile(String authorityUuid, String uuid) throws NotFoundException {
        raProfileService.deleteRaProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    public void deleteRaProfile(String raProfileUuid) throws NotFoundException {
        raProfileService.deleteRaProfile(SecuredUUID.fromString(raProfileUuid));
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.ENABLE)
    public void disableRaProfile(String authorityUuid, String uuid) throws NotFoundException {
        raProfileService.disableRaProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.ENABLE)
    public void enableRaProfile(String authorityUuid, String uuid) throws NotFoundException {
        raProfileService.enableRaProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.LIST_AUTHORIZATIONS)
    public List<SimplifiedClientDto> listUsers(String authorityUuid, String uuid) throws NotFoundException {
        return raProfileService.listClients(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.DELETE)
    public void bulkDeleteRaProfile(String authorityUuid, List<String> uuids) throws NotFoundException, ValidationException {
        raProfileService.bulkDeleteRaProfile(SecuredUUID.fromList(uuids));
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.ENABLE)
    public void bulkDisableRaProfile(List<String> uuids) throws NotFoundException {
        raProfileService.bulkDisableRaProfile(SecuredUUID.fromList(uuids));
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.ENABLE)
    public void bulkEnableRaProfile(List<String> uuids) throws NotFoundException {
        raProfileService.bulkEnableRaProfile(SecuredUUID.fromList(uuids));
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.DETAIL)
    public RaProfileAcmeDetailResponseDto getAcmeForRaProfile(String authorityUuid, String uuid) throws NotFoundException {
        return raProfileService.getAcmeForRaProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.ACTIVATE_ACME)
    public RaProfileAcmeDetailResponseDto activateAcmeForRaProfile(String authorityUuid, String uuid, String acmeProfileUuid, ActivateAcmeForRaProfileRequestDto request) throws ConnectorException, ValidationException {
        return raProfileService.activateAcmeForRaProfile(SecuredUUID.fromString(uuid), SecuredUUID.fromString(acmeProfileUuid), request);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.ACTIVATE_ACME)
    public void deactivateAcmeForRaProfile(String authorityUuid, String uuid) throws NotFoundException {
        raProfileService.deactivateAcmeForRaProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.ANY)
    public List<AttributeDefinition> listRevokeCertificateAttributes(String authorityUuid, String uuid) throws ConnectorException {
        return raProfileService.listRevokeCertificateAttributes(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.ANY)
    public List<AttributeDefinition> listIssueCertificateAttributes(String authorityUuid, String uuid) throws ConnectorException {
        return raProfileService.listIssueCertificateAttributes(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.CHECK_COMPLIANCE)
    public void checkCompliance(List<String> uuids) throws NotFoundException {
        raProfileService.checkCompliance(SecuredUUID.fromList(uuids));
    }
}
