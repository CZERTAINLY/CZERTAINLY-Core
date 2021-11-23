package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.connector.ConnectorDto;

public interface ConnectorRegistrationService {

    ConnectorDto registerConnector(ConnectorDto request) throws AlreadyExistException, NotFoundException;
}
