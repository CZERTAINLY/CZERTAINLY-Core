package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.connector.ConnectorRequestDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.connector.ConnectorDto;

public interface ConnectorRegistrationService {

    UuidDto registerConnector(ConnectorRequestDto request) throws AlreadyExistException, ConnectorException;
}
