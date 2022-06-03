package com.czertainly.core.api.web;

import com.czertainly.api.exception.*;
import com.czertainly.api.interfaces.core.web.LocationManagementController;
import com.czertainly.api.model.client.location.AddLocationRequestDto;
import com.czertainly.api.model.client.location.EditLocationRequestDto;
import com.czertainly.api.model.client.location.IssueToLocationRequestDto;
import com.czertainly.api.model.client.location.PushToLocationRequestDto;
import com.czertainly.api.model.common.AttributeDefinition;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.core.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class LocationManagementControllerImpl implements LocationManagementController {

    @Autowired
    public void setLocationService(LocationService locationService) {
        this.locationService = locationService;
    }

    private LocationService locationService;

    @Override
    public List<LocationDto> listLocations() {
        return locationService.listLocation();
    }

    @Override
    public List<LocationDto> listLocations(Boolean isEnabled) {
        return locationService.listLocations(isEnabled);
    }

    @Override
    public ResponseEntity<?> addLocation(AddLocationRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException, CertificateException {
        LocationDto locationDto = locationService.addLocation(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{locationUuid}")
                .buildAndExpand(locationDto.getUuid()).toUri();
        UuidDto dto = new UuidDto();
        dto.setUuid(locationDto.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    public LocationDto getLocation(String locationUuid) throws NotFoundException {
        return locationService.getLocation(locationUuid);
    }

    @Override
    public LocationDto editLocation(String locationUuid, EditLocationRequestDto request) throws ConnectorException, CertificateException {
        return locationService.editLocation(locationUuid, request);
    }

    @Override
    public void removeLocation(String locationUuid) throws NotFoundException {
        locationService.removeLocation(locationUuid);
    }

    @Override
    public void disableLocation(String locationUuid) throws NotFoundException {
        locationService.disableLocation(locationUuid);
    }

    @Override
    public void enableLocation(String locationUuid) throws NotFoundException {
        locationService.enableLocation(locationUuid);
    }

    @Override
    public List<AttributeDefinition> listPushAttributes(String locationUuid) throws ConnectorException {
        return locationService.listPushAttributes(locationUuid);
    }

    @Override
    public List<AttributeDefinition> listCsrAttributes(String locationUuid) throws ConnectorException {
        return locationService.listCsrAttributes(locationUuid);
    }

    @Override
    public LocationDto pushCertificate(String locationUuid, String certificateUuid, PushToLocationRequestDto request) throws ConnectorException, LocationException {
        return locationService.pushCertificateToLocation(locationUuid, certificateUuid, request);
    }

    @Override
    public LocationDto removeCertificate(String locationUuid, String certificateUuid) throws ConnectorException {
        return locationService.removeCertificateFromLocation(locationUuid, certificateUuid);
    }

    @Override
    public LocationDto issueCertificate(String locationUuid, IssueToLocationRequestDto request) throws ConnectorException, java.security.cert.CertificateException, AlreadyExistException, LocationException {
        return locationService.issueCertificateToLocation(locationUuid, request);
    }

    @Override
    public LocationDto updateLocationContent(String locationUuid) throws ConnectorException, CertificateException {
        return locationService.updateLocationContent(locationUuid);
    }

    @Override
    public LocationDto updateLocationContent(String locationUuid, String certificateUuid) throws ConnectorException, java.security.cert.CertificateException, AlreadyExistException, LocationException {
        return locationService.renewCertificateInLocation(locationUuid, certificateUuid);
    }

}
