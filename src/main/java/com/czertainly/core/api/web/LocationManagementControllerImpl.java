package com.czertainly.core.api.web;

import com.czertainly.api.exception.*;
import com.czertainly.api.interfaces.core.web.LocationManagementController;
import com.czertainly.api.model.client.certificate.LocationsResponseDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.location.AddLocationRequestDto;
import com.czertainly.api.model.client.location.EditLocationRequestDto;
import com.czertainly.api.model.client.location.IssueToLocationRequestDto;
import com.czertainly.api.model.client.location.PushToLocationRequestDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.core.auth.AuthEndpoint;
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

@RestController
public class LocationManagementControllerImpl implements LocationManagementController {

    private LocationService locationService;

    @Autowired
    public void setLocationService(LocationService locationService) {
        this.locationService = locationService;
    }

    @Override
    @AuthEndpoint(resourceName = Resource.LOCATION)
    public LocationsResponseDto listLocations(final SearchRequestDto request) {
        return locationService.listLocations(SecurityFilter.create(), request);
    }

    @Override
    public ResponseEntity<?> addLocation(String entityUuid, AddLocationRequestDto request) throws ConnectorException, AlreadyExistException, LocationException, AttributeException {
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
    public LocationDto editLocation(String entityUuid, String locationUuid, EditLocationRequestDto request) throws ConnectorException, LocationException, AttributeException {
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
    public List<BaseAttribute> listPushAttributes(String entityUuid, String locationUuid) throws NotFoundException, LocationException {
        return locationService.listPushAttributes(SecuredParentUUID.fromString(entityUuid), SecuredUUID.fromString(locationUuid));
    }

    @Override
    public List<BaseAttribute> listCsrAttributes(String entityUuid, String locationUuid) throws NotFoundException, LocationException {
        return locationService.listCsrAttributes(SecuredParentUUID.fromString(entityUuid), SecuredUUID.fromString(locationUuid));
    }

    @Override
    public LocationDto pushCertificate(String entityUuid, String locationUuid, String certificateUuid, PushToLocationRequestDto request) throws NotFoundException, LocationException, AttributeException {
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

    @Override
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return locationService.getSearchableFieldInformationByGroup();
    }
}
