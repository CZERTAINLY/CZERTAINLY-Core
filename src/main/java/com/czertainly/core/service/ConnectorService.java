package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.connector.ConnectDto;
import com.czertainly.api.model.client.connector.ConnectRequestDto;
import com.czertainly.api.model.client.connector.ConnectorRequestDto;
import com.czertainly.api.model.client.connector.ConnectorUpdateRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.HealthDto;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.common.attribute.RequestAttributeDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.connector.BaseFunctionGroupDto;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;
import java.util.Map;

public interface ConnectorService {

    List<ConnectorDto> listConnectors(SecurityFilter filter);
    
    Map<FunctionGroupCode, Map<String, List<AttributeDefinition>>> getAllAttributesOfConnector(SecuredUUID uuid) throws NotFoundException, ConnectorException;

    List<ConnectorDto> listConnectorsByFunctionGroup(SecurityFilter filter, FunctionGroupCode functionGroup);

    List<ConnectorDto> listConnectors(SecurityFilter filter, FunctionGroupCode functionGroup, String kind) throws NotFoundException;

    ConnectorDto getConnector(SecuredUUID uuid) throws NotFoundException, ConnectorException;

    Connector getConnectorEntity(SecuredUUID uuid) throws NotFoundException;

    ConnectorDto createConnector(ConnectorRequestDto request) throws AlreadyExistException, ConnectorException;

    ConnectorDto createNewWaitingConnector(ConnectorRequestDto request) throws AlreadyExistException, ConnectorException;

    ConnectorDto createConnector(ConnectorDto request, ConnectorStatus connectorStatus) throws NotFoundException, AlreadyExistException;

    ConnectorDto editConnector(SecuredUUID uuid, ConnectorUpdateRequestDto request) throws ConnectorException;

    void deleteConnector(SecuredUUID uuid) throws NotFoundException;

    void approve(List<SecuredUUID> uuids) throws NotFoundException, ValidationException;

    List<ConnectDto> connect(ConnectRequestDto request) throws ValidationException, ConnectorException;

    List<ConnectDto> reconnect(SecuredUUID uuid) throws ValidationException, NotFoundException, ConnectorException;

    List<ConnectDto> validateConnector(ConnectorDto request) throws ConnectorException;

    List<ConnectDto> validateConnector(List<? extends BaseFunctionGroupDto> functions, SecuredUUID uuid);

    void reconnect(List<SecuredUUID> uuids) throws ValidationException, NotFoundException, ConnectorException;

    void approve(SecuredUUID uuid) throws NotFoundException, ValidationException;

    HealthDto checkHealth(SecuredUUID uuid) throws NotFoundException, ConnectorException;

    List<AttributeDefinition> getAttributes(SecuredUUID uuid, FunctionGroupCode functionGroup, String functionGroupType) throws NotFoundException, ConnectorException;

    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.VALIDATE)
    void validateAttributes(SecuredUUID uuid, FunctionGroupCode functionGroup, List<RequestAttributeDto> attributes, String functionGroupType) throws NotFoundException, ValidationException, ConnectorException;

    List<AttributeDefinition> mergeAndValidateAttributes(SecuredUUID uuid, FunctionGroupCode functionGroup, List<RequestAttributeDto> attributes, String functionGroupType) throws NotFoundException, ConnectorException;

    List<BulkActionMessageDto> bulkDeleteConnector(List<SecuredUUID> uuids) throws ValidationException, NotFoundException;

    List<BulkActionMessageDto> forceDeleteConnector(List<SecuredUUID> uuids) throws ValidationException, NotFoundException;
}
