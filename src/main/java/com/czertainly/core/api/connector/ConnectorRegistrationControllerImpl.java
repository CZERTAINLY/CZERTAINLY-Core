package com.czertainly.core.api.connector;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.connector.ConnectorRegistrationController;
import com.czertainly.api.model.client.connector.ConnectorRequestDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.service.ConnectorRegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConnectorRegistrationControllerImpl implements ConnectorRegistrationController {

    private ConnectorRegistrationService connectorRegistrationService;

    @Autowired
    public void setConnectorRegistrationService(ConnectorRegistrationService connectorRegistrationService) {
        this.connectorRegistrationService = connectorRegistrationService;
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.CONNECTOR, operation = Operation.REGISTER)
    public UuidDto register(@RequestBody ConnectorRequestDto request) throws ConnectorException, AlreadyExistException, AttributeException, NotFoundException {
        return connectorRegistrationService.registerConnector(request);
    }
}
