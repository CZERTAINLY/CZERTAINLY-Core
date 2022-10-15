package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.LocationException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.LocationManagementController;
import com.czertainly.api.model.client.location.AddLocationRequestDto;
import com.czertainly.api.model.client.location.EditLocationRequestDto;
import com.czertainly.api.model.client.location.IssueToLocationRequestDto;
import com.czertainly.api.model.client.location.PushToLocationRequestDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@RestController
public class LocationManagementControllerImpl implements LocationManagementController {

    private LocationService locationService;

    @Autowired
    public void setLocationService(LocationService locationService) {
        this.locationService = locationService;
    }

    @Override
    @AuthEndpoint(resourceName = Resource.LOCATION)
    public List<LocationDto> listLocations(Optional<Boolean> enabled) {
        return locationService.listLocations(SecurityFilter.create(), enabled);
    }

    @Override
    public ResponseEntity<?> addLocation(String entityUuid, AddLocationRequestDto request) throws NotFoundException, AlreadyExistException, LocationException {
        LocationDto locationDto = locationService.addLocation(SecuredParentUUID.fromString(entityUuid), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{locationUuid}")
                .buildAndExpand(locationDto.getUuid()).toUri();
        UuidDto dto = new UuidDto();
        dto.setUuid(locationDto.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    public LocationDto getLocation(String entityUuid, String locationUuid) throws NotFoundException {
        return locationService.getLocation(SecuredParentUUID.fromString(entityUuid), SecuredUUID.fromString(locationUuid));
    }

    @Override
    public LocationDto editLocation(String entityUuid, String locationUuid, EditLocationRequestDto request) throws NotFoundException, LocationException {
        return locationService.editLocation(SecuredParentUUID.fromString(entityUuid), SecuredUUID.fromString(locationUuid), request);
    }

    @Override
    public void deleteLocation(String entityUuid, String locationUuid) throws NotFoundException {
        locationService.deleteLocation(SecuredParentUUID.fromString(entityUuid), SecuredUUID.fromString(locationUuid));
    }

    @Override
    public void disableLocation(String entityUuid, String locationUuid) throws NotFoundException {
        locationService.disableLocation(SecuredParentUUID.fromString(entityUuid), SecuredUUID.fromString(locationUuid));
    }

    @Override
    public void enableLocation(String entityUuid, String locationUuid) throws NotFoundException {
        locationService.enableLocation(SecuredParentUUID.fromString(entityUuid), SecuredUUID.fromString(locationUuid));
    }

    @Override
    public List<AttributeDefinition> listPushAttributes(String entityUuid, String locationUuid) throws NotFoundException, LocationException {
        return locationService.listPushAttributes(SecuredParentUUID.fromString(entityUuid), SecuredUUID.fromString(locationUuid));
    }

    @Override
    public List<AttributeDefinition> listCsrAttributes(String entityUuid, String locationUuid) throws NotFoundException, LocationException {
        return locationService.listCsrAttributes(SecuredParentUUID.fromString(entityUuid), SecuredUUID.fromString(locationUuid));
    }

    @Override
    public LocationDto pushCertificate(String entityUuid, String locationUuid, String certificateUuid, PushToLocationRequestDto request) throws NotFoundException, LocationException {
        return locationService.pushCertificateToLocation(
                SecuredParentUUID.fromString(entityUuid),
                SecuredUUID.fromString(locationUuid),
                certificateUuid,
                request
        );
    }

    @Override
    public LocationDto removeCertificate(String entityUuid, String locationUuid, String certificateUuid) throws NotFoundException, LocationException {
        return locationService.removeCertificateFromLocation(
                SecuredParentUUID.fromString(entityUuid),
                SecuredUUID.fromString(locationUuid),
                certificateUuid
        );
    }

    @Override
    public LocationDto issueCertificate(String entityUuid, String locationUuid, IssueToLocationRequestDto request) throws ConnectorException, LocationException {
        return locationService.issueCertificateToLocation(
                SecuredParentUUID.fromString(entityUuid),
                SecuredUUID.fromString(locationUuid),
                request.getRaProfileUuid(),
                request
        );
    }

    @Override
    public LocationDto updateLocationContent(String entityUuid, String locationUuid) throws NotFoundException, LocationException {
        return locationService.updateLocationContent(SecuredParentUUID.fromString(entityUuid), SecuredUUID.fromString(locationUuid));
    }

    @Override
    public LocationDto renewCertificateInLocation(String entityUuid, String locationUuid, String certificateUuid) throws ConnectorException, LocationException {
        return locationService.renewCertificateInLocation(
                SecuredParentUUID.fromString(entityUuid),
                SecuredUUID.fromString(locationUuid),
                certificateUuid);
    }

}
