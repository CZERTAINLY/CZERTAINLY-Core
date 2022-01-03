package com.czertainly.core.service;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.RequestAttributeCallback;
import com.czertainly.api.model.core.connector.FunctionGroupCode;

public interface CallbackService {

    Object callback(String uuid, FunctionGroupCode functionGroup, String kind, RequestAttributeCallback callback) throws NotFoundException, ConnectorException, ValidationException;

    Object raProfileCallback(String authorityUuid, RequestAttributeCallback callback) throws NotFoundException, ConnectorException, ValidationException;
}
