package com.czertainly.core.api.web;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.CallbackController;
import com.czertainly.api.model.common.attribute.v2.callback.RequestAttributeCallback;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.service.CallbackService;
import com.czertainly.core.util.converter.ResourceCodeConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CallbackControllerImpl implements CallbackController {

    private CallbackService callbackService;

    @Autowired
    public void setCallbackService(CallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @InitBinder
    public void initBinder(final WebDataBinder webdataBinder) {
        webdataBinder.registerCustomEditor(Resource.class, new ResourceCodeConverter());
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.ATTRIBUTE, affiliatedResource = Resource.CONNECTOR, operation = Operation.ATTRIBUTE_CALLBACK)
    public Object callback(
            @LogResource(uuid = true, affiliated = true) String uuid,
            String functionGroup,
            @LogResource(name = true) String kind,
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
    @AuditLogged(module = Module.CORE, resource = Resource.ATTRIBUTE, affiliatedResource = Resource.CONNECTOR, operation = Operation.ATTRIBUTE_CALLBACK)
    public Object resourceCallback(
            Resource resource,
            String parentObjectUuid,
            RequestAttributeCallback callback
    ) throws NotFoundException, ConnectorException, ValidationException {
        return callbackService.resourceCallback(
                resource,
                parentObjectUuid,
                callback
        );
    }

}
