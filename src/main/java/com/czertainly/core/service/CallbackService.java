package com.czertainly.core.service;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.attribute.v2.callback.RequestAttributeCallback;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.core.aop.AuditLogged;

public interface CallbackService {

    Object callback(String uuid, FunctionGroupCode functionGroup, String kind, RequestAttributeCallback callback) throws NotFoundException, ConnectorException, ValidationException;

    Object keyCallback(String tokenInstanceUuid, RequestAttributeCallback callback) throws ConnectorException, ValidationException;

    Object raProfileCallback(String authorityUuid, RequestAttributeCallback callback) throws NotFoundException, ConnectorException, ValidationException;
}
