package com.czertainly.core.api.web;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.CallbackController;
import com.czertainly.api.model.common.attribute.v2.callback.RequestAttributeCallback;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
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
    public Object callback(
            String uuid,
            String functionGroup,
            String kind,
            RequestAttributeCallback callback
    ) throws NotFoundException, ConnectorException, ValidationException {
        return callbackService.callback(
                uuid,
                FunctionGroupCode.findByCode(functionGroup),
                kind,
                callback
        );
    }

    @Override
    public Object resourceCallback(
            Resource resource,
            String resourceUuid,
            RequestAttributeCallback callback
    ) throws NotFoundException, ConnectorException, ValidationException {
        return callbackService.resourceCallback(
                resource,
                resourceUuid,
                callback
        );
    }

}
