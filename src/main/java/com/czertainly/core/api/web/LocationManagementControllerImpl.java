package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
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
import com.czertainly.core.model.auth.ActionName;
import com.czertainly.core.model.auth.ResourceName;
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

    @Autowired
    public void setLocationService(LocationService locationService) {
        this.locationService = locationService;
    }

    private LocationService locationService;

    @Override
    @AuthEndpoint(resourceName = ResourceName.LOCATION, actionName = ActionName.LIST)
    public List<LocationDto> listLocations(Optional<Boolean> enabled) {
        return locationService.listLocations(enabled);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.LOCATION, actionName = ActionName.ADD)
    public ResponseEntity<?> addLocation(AddLocationRequestDto request) throws NotFoundException, AlreadyExistException, LocationException {
        LocationDto locationDto = locationService.addLocation(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{locationUuid}")
                .buildAndExpand(locationDto.getUuid()).toUri();
        UuidDto dto = new UuidDto();
        dto.setUuid(locationDto.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.LOCATION, actionName = ActionName.DETAIL)
    public LocationDto getLocation(String locationUuid) throws NotFoundException {
        return locationService.getLocation(locationUuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.LOCATION, actionName = ActionName.UPDATE)
    public LocationDto editLocation(String locationUuid, EditLocationRequestDto request) throws NotFoundException, LocationException {
        return locationService.editLocation(locationUuid, request);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.LOCATION, actionName = ActionName.DELETE)
    public void deleteLocation(String locationUuid) throws NotFoundException {
        locationService.deleteLocation(locationUuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.LOCATION, actionName = ActionName.DISABLE)
    public void disableLocation(String locationUuid) throws NotFoundException {
        locationService.disableLocation(locationUuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.LOCATION, actionName = ActionName.ENABLE)
    public void enableLocation(String locationUuid) throws NotFoundException {
        locationService.enableLocation(locationUuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.LOCATION, actionName = ActionName.LIST_ATTRIBUTES)
    public List<AttributeDefinition> listPushAttributes(String locationUuid) throws NotFoundException, LocationException {
        return locationService.listPushAttributes(locationUuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.LOCATION, actionName = ActionName.LIST_ATTRIBUTES)
    public List<AttributeDefinition> listCsrAttributes(String locationUuid) throws NotFoundException, LocationException {
        return locationService.listCsrAttributes(locationUuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.LOCATION, actionName = ActionName.PUSH)
    public LocationDto pushCertificate(String locationUuid, String certificateUuid, PushToLocationRequestDto request) throws NotFoundException, LocationException {
        return locationService.pushCertificateToLocation(locationUuid, certificateUuid, request);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.LOCATION, actionName = ActionName.REMOVE_CERTIFICATE_FROM_LOCATION)
    public LocationDto removeCertificate(String locationUuid, String certificateUuid) throws NotFoundException, LocationException {
        return locationService.removeCertificateFromLocation(locationUuid, certificateUuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.LOCATION, actionName = ActionName.ISSUE_CERTIFICATE_TO_LOCATION)
    public LocationDto issueCertificate(String locationUuid, IssueToLocationRequestDto request) throws NotFoundException, LocationException {
        return locationService.issueCertificateToLocation(locationUuid, request);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.LOCATION, actionName = ActionName.UPDATE)
    public LocationDto updateLocationContent(String locationUuid) throws NotFoundException, LocationException {
        return locationService.updateLocationContent(locationUuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.LOCATION, actionName = ActionName.RENEW)
    public LocationDto renewCertificateInLocation(String locationUuid, String certificateUuid) throws NotFoundException, LocationException {
        return locationService.renewCertificateInLocation(locationUuid, certificateUuid);
    }

}
