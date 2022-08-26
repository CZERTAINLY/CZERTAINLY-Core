package com.czertainly.core.api.web;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.CallbackController;
import com.czertainly.api.model.common.attribute.RequestAttributeCallback;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.service.CallbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CallbackControllerImpl implements CallbackController {

    @Autowired
    private CallbackService callbackService;

    @Override
    @AuthEndpoint(resourceName = Resource.CONNECTOR, actionName = ResourceAction.ANY)
    public Object callback(@PathVariable String uuid, String functionGroup, String kind, @RequestBody RequestAttributeCallback callback) throws NotFoundException, ConnectorException, ValidationException {
        return callbackService.callback(uuid, FunctionGroupCode.findByCode(functionGroup), kind, callback);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.RA_PROFILE, actionName = ResourceAction.ANY)
    public Object raProfileCallback(String authorityUuid, @RequestBody RequestAttributeCallback callback) throws NotFoundException, ConnectorException, ValidationException {
        return callbackService.raProfileCallback(authorityUuid, callback);
    }
}
