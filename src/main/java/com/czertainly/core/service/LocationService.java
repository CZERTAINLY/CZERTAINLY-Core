package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.location.AddLocationRequestDto;
import com.czertainly.api.model.client.location.EditLocationRequestDto;
import com.czertainly.api.model.client.location.IssueToLocationRequestDto;
import com.czertainly.api.model.client.location.PushToLocationRequestDto;
import com.czertainly.api.model.common.AttributeDefinition;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.core.dao.entity.Location;

import java.util.List;

public interface LocationService {

    List<LocationDto> listLocation();

    List<LocationDto> listLocations(Boolean isEnabled);

    LocationDto addLocation(AddLocationRequestDto dto) throws AlreadyExistException, LocationException, NotFoundException;

    LocationDto getLocation(String locationUuid) throws NotFoundException;

    LocationDto editLocation(String locationUuid, EditLocationRequestDto dto) throws NotFoundException, LocationException;

    void removeLocation(String locationUuid) throws NotFoundException;

    void enableLocation(String locationUuid) throws NotFoundException;

    void disableLocation(String locationUuid) throws NotFoundException;

    List<AttributeDefinition> listPushAttributes(String locationUuid) throws NotFoundException, LocationException;

    List<AttributeDefinition> listCsrAttributes(String locationUuid) throws NotFoundException, LocationException;

    List<Location> getCertificateLocations(String certificateUuid) throws NotFoundException;

    LocationDto removeCertificateFromLocation(String locationUuid, String certificateUuid) throws NotFoundException, LocationException;

    void removeCertificateFromLocations(String certificateUuid) throws NotFoundException;

    LocationDto pushCertificateToLocation(String locationUuid, String certificateUuid, PushToLocationRequestDto request) throws NotFoundException, LocationException;

    LocationDto issueCertificateToLocation(String locationUuid, IssueToLocationRequestDto request) throws NotFoundException, LocationException;

    LocationDto updateLocationContent(String locationUuid) throws NotFoundException, LocationException;

    LocationDto renewCertificateInLocation(String locationUuid, String certificateUuid) throws NotFoundException, LocationException;

}
