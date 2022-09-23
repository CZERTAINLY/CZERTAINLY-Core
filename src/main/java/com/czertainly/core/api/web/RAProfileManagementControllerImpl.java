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
    @AuthEndpoint(resourceName = Resource.RA_PROFILE)
    public List<RaProfileDto> listRaProfiles(Optional<Boolean> enabled) {
        return raProfileService.listRaProfiles(SecurityFilter.create(), enabled);
    }

    @Override
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
    public RaProfileDto getRaProfile(String authorityUuid, String raProfileUuid) throws NotFoundException {
        return raProfileService.getRaProfile(SecuredUUID.fromString(raProfileUuid));
    }

    @Override
    public RaProfileDto getRaProfile(String raProfileUuid) throws NotFoundException {
        return raProfileService.getRaProfile(SecuredUUID.fromString(raProfileUuid));
    }

    @Override
    public RaProfileDto editRaProfile(String authorityUuid, String raProfileUuid, EditRaProfileRequestDto request)
            throws ConnectorException {
        return raProfileService.editRaProfile(SecuredUUID.fromString(authorityUuid), SecuredUUID.fromString(raProfileUuid), request);
    }

    @Override
    public void deleteRaProfile(String authorityUuid, String uuid) throws NotFoundException {
        raProfileService.deleteRaProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    public void deleteRaProfile(String raProfileUuid) throws NotFoundException {
        raProfileService.deleteRaProfile(SecuredUUID.fromString(raProfileUuid));
    }

    @Override
    public void disableRaProfile(String authorityUuid, String raProfileUuid) throws NotFoundException {
        raProfileService.disableRaProfile(SecuredUUID.fromString(raProfileUuid));
    }

    @Override
    public void enableRaProfile(String authorityUuid, String raProfileUuid) throws NotFoundException {
        raProfileService.enableRaProfile(SecuredUUID.fromString(raProfileUuid));
    }

    @Override
    public List<SimplifiedClientDto> listUsers(String authorityUuid, String raProfileUuid) throws NotFoundException {
        return raProfileService.listClients(SecuredUUID.fromString(raProfileUuid));
    }

    @Override
    public void bulkDeleteRaProfile(List<String> uuids) throws NotFoundException, ValidationException {
        raProfileService.bulkDeleteRaProfile(SecuredUUID.fromList(uuids));
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
    public RaProfileAcmeDetailResponseDto getAcmeForRaProfile(String authorityUuid, String raProfileUuid) throws NotFoundException {
        return raProfileService.getAcmeForRaProfile(SecuredUUID.fromString(raProfileUuid));
    }

    @Override
    public RaProfileAcmeDetailResponseDto activateAcmeForRaProfile(String authorityUuid, String raProfileUuid, String acmeProfileUuid, ActivateAcmeForRaProfileRequestDto request) throws ConnectorException, ValidationException {
        return raProfileService.activateAcmeForRaProfile(SecuredUUID.fromString(raProfileUuid), SecuredUUID.fromString(acmeProfileUuid), request);
    }

    @Override
    public void deactivateAcmeForRaProfile(String authorityUuid, String uuid) throws NotFoundException {
        raProfileService.deactivateAcmeForRaProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    public List<AttributeDefinition> listRevokeCertificateAttributes(String authorityUuid, String raProfileUuid) throws ConnectorException {
        return raProfileService.listRevokeCertificateAttributes(SecuredUUID.fromString(raProfileUuid));
    }

    @Override
    public List<AttributeDefinition> listIssueCertificateAttributes(String authorityUuid, String raProfileUuid) throws ConnectorException {
        return raProfileService.listIssueCertificateAttributes(SecuredUUID.fromString(raProfileUuid));
    }

    @Override
    public void checkCompliance(List<String> uuids) throws NotFoundException {
        raProfileService.checkCompliance(SecuredUUID.fromList(uuids));
    }
}
