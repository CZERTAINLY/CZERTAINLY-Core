package com.czertainly.core.api.connector;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.interfaces.core.connector.ConnectorRegistrationController;
import com.czertainly.api.model.client.connector.ConnectorRequestDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.ActionName;
import com.czertainly.core.model.auth.ResourceName;
import com.czertainly.core.service.ConnectorRegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConnectorRegistrationControllerImpl implements ConnectorRegistrationController {

    @Autowired
    private ConnectorRegistrationService connectorRegistrationService;

    @Override
    @AuthEndpoint(resourceName = ResourceName.CONNECTOR, actionName = ActionName.REGISTER)
    public UuidDto register(@RequestBody ConnectorRequestDto request) throws ConnectorException, AlreadyExistException {
        return connectorRegistrationService.registerConnector(request);
    }
}
