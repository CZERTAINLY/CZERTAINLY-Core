package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.model.client.connector.ConnectorRequestDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.service.ConnectorRegistrationService;
import com.czertainly.core.service.ConnectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@Service
@Transactional
public class ConnectorRegistrationServiceImpl implements ConnectorRegistrationService {
    private static final Logger logger = LoggerFactory.getLogger(ConnectorRegistrationServiceImpl.class);

    @Autowired
    private ConnectorService connectorService;

    @Override
    @AuditLogged(originator = ObjectType.CONNECTOR, affected = ObjectType.CONNECTOR, operation = OperationType.CREATE)
    public UuidDto registerConnector(ConnectorRequestDto request) throws AlreadyExistException, ConnectorException {
        ConnectorDto connectorDto = connectorService.createNewWaitingConnector(request);
        logger.info("Connector {} registered and is waiting for approval.", request.getName());
        UuidDto dto = new UuidDto();
        dto.setUuid(connectorDto.getUuid());
        return dto;
    }
}
