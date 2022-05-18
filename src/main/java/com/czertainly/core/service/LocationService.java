package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.location.AddLocationRequestDto;
import com.czertainly.api.model.client.location.EditLocationRequestDto;
import com.czertainly.api.model.common.AttributeDefinition;
import com.czertainly.api.model.core.location.LocationDto;

import java.util.List;

public interface LocationService {

    List<LocationDto> listLocation();

    List<LocationDto> listLocations(Boolean isEnabled);

    LocationDto addLocation(AddLocationRequestDto dto) throws AlreadyExistException, ValidationException, ConnectorException;

    LocationDto getLocation(String locationUuid) throws NotFoundException;

    LocationDto editLocation(String locationUuid, EditLocationRequestDto dto) throws ConnectorException;

    void removeLocation(String locationUuid) throws NotFoundException;

    void enableLocation(String locationUuid) throws NotFoundException;

    void disableLocation(String locationUuid) throws NotFoundException;

    List<AttributeDefinition> listPushAttributes(String locationUuid) throws ConnectorException;

    List<AttributeDefinition> listCsrAttributes(String locationUuid) throws ConnectorException;
}
