package com.czertainly.core.api.connector;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.connector.ConnectorRegistrationController;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.core.service.ConnectorRegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConnectorRegistrationControllerImpl implements ConnectorRegistrationController {

    @Autowired
    private ConnectorRegistrationService connectorRegistrationService;

    @Override
    public ConnectorDto register(@RequestBody ConnectorDto request) throws NotFoundException, AlreadyExistException {
        return connectorRegistrationService.registerConnector(request);
    }
}
