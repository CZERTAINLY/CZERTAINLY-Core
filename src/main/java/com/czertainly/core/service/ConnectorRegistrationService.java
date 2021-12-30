package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.connector.ConnectorDto;

public interface ConnectorRegistrationService {

    UuidDto registerConnector(ConnectorDto request) throws AlreadyExistException, NotFoundException;
}
