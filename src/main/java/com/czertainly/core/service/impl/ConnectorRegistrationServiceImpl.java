package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.connector.ConnectorRequestDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.core.service.ConnectorRegistrationService;
import com.czertainly.core.service.ConnectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;

@Service
@Transactional
public class ConnectorRegistrationServiceImpl implements ConnectorRegistrationService {
    private static final Logger logger = LoggerFactory.getLogger(ConnectorRegistrationServiceImpl.class);

    private ConnectorService connectorService;

    @Autowired
    public void setConnectorService(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @Override
    public UuidDto registerConnector(ConnectorRequestDto request) throws AlreadyExistException, ConnectorException, AttributeException, NotFoundException {
        ConnectorDto connectorDto = connectorService.createNewWaitingConnector(request);
        logger.info("Connector {} registered and is waiting for approval.", request.getName());
        UuidDto dto = new UuidDto();
        dto.setUuid(connectorDto.getUuid());
        return dto;
    }
}
