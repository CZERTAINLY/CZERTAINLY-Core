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

    LocationDto addLocation(AddLocationRequestDto dto) throws AlreadyExistException, ValidationException, ConnectorException, CertificateException;

    LocationDto getLocation(String locationUuid) throws NotFoundException;

    LocationDto editLocation(String locationUuid, EditLocationRequestDto dto) throws ConnectorException, com.czertainly.api.exception.CertificateException;

    void removeLocation(String locationUuid) throws NotFoundException;

    void enableLocation(String locationUuid) throws NotFoundException;

    void disableLocation(String locationUuid) throws NotFoundException;

    List<AttributeDefinition> listPushAttributes(String locationUuid) throws ConnectorException;

    List<AttributeDefinition> listCsrAttributes(String locationUuid) throws ConnectorException;

    List<Location> getCertificateLocations(String certificateUuid) throws NotFoundException;

    LocationDto removeCertificateFromLocation(String locationUuid, String certificateUuid) throws ConnectorException;

    void removeCertificateFromLocations(String certificateUuid) throws ConnectorException;

    LocationDto pushCertificateToLocation(String locationUuid, String certificateUuid, PushToLocationRequestDto request) throws ConnectorException;

    LocationDto issueCertificateToLocation(String locationUuid, IssueToLocationRequestDto request) throws ConnectorException, java.security.cert.CertificateException, AlreadyExistException;

    LocationDto updateLocationContent(String locationUuid) throws ConnectorException, CertificateException;

}
