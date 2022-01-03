package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.model.client.connector.ConnectorRequestDto;
import com.czertainly.api.model.common.UuidDto;

public interface ConnectorRegistrationService {

    UuidDto registerConnector(ConnectorRequestDto request) throws AlreadyExistException, ConnectorException;
}
