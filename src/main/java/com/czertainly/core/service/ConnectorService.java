package com.czertainly.core.service;

import com.czertainly.api.core.modal.ObjectType;
import com.czertainly.api.core.modal.OperationType;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.*;
import com.czertainly.api.model.connector.*;

import java.util.List;
import java.util.Map;

public interface ConnectorService {

    List<ConnectorDto> listConnectors();
    
    Map<FunctionGroupCode, Map<String, List<AttributeDefinition>>> getAllAttributesOfConnector(String uuid) throws NotFoundException, ConnectorException;

    List<ConnectorDto> listConnectorsByFunctionGroup(FunctionGroupCode functionGroup);

    List<ConnectorDto> listConnectors(FunctionGroupCode functionGroup, String kind) throws NotFoundException;

    ConnectorDto getConnector(String uuid) throws NotFoundException, ConnectorException;

    Connector getConnectorEntity(String uuid) throws NotFoundException;

    ConnectorDto createConnector(ConnectorRequestDto request) throws AlreadyExistException, ConnectorException;

    ConnectorDto createConnector(ConnectorDto request, ConnectorStatus connectorStatus) throws NotFoundException, AlreadyExistException;

    ConnectorDto updateConnector(String uuid, ConnectorRequestDto request) throws ConnectorException;

    void removeConnector(String uuid) throws NotFoundException;

    void approve(List<String> uuids) throws NotFoundException, ValidationException;

    List<ConnectDto> connect(ConnectorDto request) throws ValidationException, ConnectorException;

    List<ConnectDto> reconnect(String uuid) throws ValidationException, NotFoundException, ConnectorException;

    List<ConnectDto> validateConnector(ConnectorDto request) throws ConnectorException;

    List<ConnectDto> validateConnector(List<? extends BaseFunctionGroupDto> functions, String uuid);

    void reconnect(List<String> uuids) throws ValidationException, NotFoundException, ConnectorException;

    void approve(String uuid) throws NotFoundException, ValidationException;

    HealthDto checkHealth(String uuid) throws NotFoundException, ConnectorException;

    List<AttributeDefinition> getAttributes(String uuid, FunctionGroupCode functionGroup, String functionGroupType) throws NotFoundException, ConnectorException;

    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.VALIDATE)
    boolean validateAttributes(String uuid, FunctionGroupCode functionGroup, List<AttributeDefinition> attributes, String functionGroupType) throws NotFoundException, ValidationException, ConnectorException;

    List<AttributeDefinition> mergeAndValidateAttributes(String uuid, FunctionGroupCode functionGroup, List<AttributeDefinition> attributes, String functionGroupType) throws NotFoundException, ConnectorException;

    Object callback(String uuid, AttributeCallback callback) throws NotFoundException, ConnectorException, ValidationException;

    List<ForceDeleteMessageDto> bulkRemoveConnector(List<String> uuids) throws ValidationException, NotFoundException;

    void bulkForceRemoveConnector(List<String> uuids) throws ValidationException, NotFoundException;
}
