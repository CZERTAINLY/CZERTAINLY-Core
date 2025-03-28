package com.czertainly.core.api.web;

import com.czertainly.api.exception.*;
import com.czertainly.api.interfaces.core.web.ConnectorController;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.connector.ConnectDto;
import com.czertainly.api.model.client.connector.ConnectRequestDto;
import com.czertainly.api.model.client.connector.ConnectorRequestDto;
import com.czertainly.api.model.client.connector.ConnectorUpdateRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.HealthDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.util.converter.ConnectorStatusConverter;
import com.czertainly.core.util.converter.FunctionGroupCodeConverter;
import com.czertainly.core.util.converter.OptionalEnumConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
public class ConnectorControllerImpl implements ConnectorController {

    private ConnectorService connectorService;

    @Autowired
    public void setConnectorService(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @InitBinder
    public void initBinder(final WebDataBinder webdataBinder) {
        webdataBinder.registerCustomEditor(FunctionGroupCode.class, new FunctionGroupCodeConverter());
        webdataBinder.registerCustomEditor(ConnectorStatusConverter.class, new ConnectorStatusConverter());
        webdataBinder.registerCustomEditor(Optional.class, new OptionalEnumConverter());
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CONNECTOR)
    @AuditLogged(module = Module.CORE, resource = Resource.CONNECTOR, operation = Operation.LIST)
    public List<ConnectorDto> listConnectors(
            @RequestParam Optional<FunctionGroupCode> functionGroup,
            @RequestParam Optional<String> kind,
            @RequestParam Optional<ConnectorStatus> status) throws NotFoundException {
        return connectorService.listConnectors(SecurityFilter.create(), functionGroup, kind, status);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.CONNECTOR, operation = Operation.APPROVE)
    public void bulkApprove(@LogResource(uuid = true) List<String> uuids) throws NotFoundException, ValidationException {
        connectorService.approve(SecuredUUID.fromList(uuids));
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.CONNECTOR, operation = Operation.RECONNECT)
    public void bulkReconnect(@LogResource(uuid = true) List<String> uuids) throws ValidationException, NotFoundException, ConnectorException {
        connectorService.reconnect(SecuredUUID.fromList(uuids));
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.CONNECTOR, operation = Operation.DETAIL)
    public ConnectorDto getConnector(@LogResource(uuid = true) @PathVariable String uuid) throws NotFoundException, ConnectorException {
        return connectorService.getConnector(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.CONNECTOR, operation = Operation.CREATE)
    public ResponseEntity<?> createConnector(@RequestBody ConnectorRequestDto request)
            throws AlreadyExistException, ConnectorException, AttributeException, NotFoundException {
        ConnectorDto connectorDto = connectorService.createConnector(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}")
                .buildAndExpand(connectorDto.getUuid()).toUri();
        UuidDto dto = new UuidDto();
        dto.setUuid(connectorDto.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.CONNECTOR, operation = Operation.UPDATE)
    public ConnectorDto editConnector(@LogResource(uuid = true) @PathVariable String uuid, @RequestBody ConnectorUpdateRequestDto request)
            throws ConnectorException, AttributeException, NotFoundException {
        return connectorService.editConnector(SecuredUUID.fromString(uuid), request);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.CONNECTOR, operation = Operation.DELETE)
    public void deleteConnector(@LogResource(uuid = true) @PathVariable String uuid) throws NotFoundException {
        connectorService.deleteConnector(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.CONNECTOR, operation = Operation.CONNECT)
    public List<ConnectDto> connect(@RequestBody ConnectRequestDto request) throws ValidationException, ConnectorException {
        return connectorService.connect(request);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.CONNECTOR, operation = Operation.RECONNECT)
    public List<ConnectDto> reconnect(@LogResource(uuid = true) @PathVariable String uuid) throws ValidationException, NotFoundException, ConnectorException {
        return connectorService.reconnect(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.CONNECTOR, operation = Operation.APPROVE)
    public void approve(@LogResource(uuid = true) @PathVariable String uuid) throws NotFoundException, ValidationException {
        connectorService.approve(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.CONNECTOR, operation = Operation.CHECK_HEALTH)
    public HealthDto checkHealth(@LogResource(uuid = true) @PathVariable String uuid) throws NotFoundException, ValidationException, ConnectorException {
        return connectorService.checkHealth(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.ATTRIBUTE, affiliatedResource = Resource.CONNECTOR, operation = Operation.LIST_ATTRIBUTES)
    public List<BaseAttribute> getAttributes(@LogResource(uuid = true, affiliated = true) @PathVariable String uuid,
                                             @PathVariable FunctionGroupCode functionGroup,
                                             @LogResource(name = true) @PathVariable String kind) throws NotFoundException, ConnectorException {
        return connectorService.getAttributes(SecuredUUID.fromString(uuid), functionGroup, kind);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.ATTRIBUTE, affiliatedResource = Resource.CONNECTOR, operation = Operation.VALIDATE_ATTRIBUTES)
    public void validateAttributes(@LogResource(uuid = true, affiliated = true) @PathVariable String uuid,
                                   @PathVariable String functionGroup,
                                   @LogResource(name = true) @PathVariable String kind,
                                   @RequestBody List<RequestAttributeDto> attributes)
            throws NotFoundException, ConnectorException {
        connectorService.validateAttributes(SecuredUUID.fromString(uuid), FunctionGroupCode.findByCode(functionGroup), attributes,
                kind);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.ATTRIBUTE, affiliatedResource = Resource.CONNECTOR, operation = Operation.LIST_ATTRIBUTES)
    public Map<FunctionGroupCode, Map<String, List<BaseAttribute>>> getAttributesAll(@LogResource(uuid = true, affiliated = true) String uuid) throws NotFoundException, ConnectorException {
        return connectorService.getAllAttributesOfConnector(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.CONNECTOR, operation = Operation.DELETE)
    public List<BulkActionMessageDto> bulkDeleteConnector(@LogResource(uuid = true) List<String> uuids) throws NotFoundException, ValidationException, ConnectorException {
        return connectorService.bulkDeleteConnector(SecuredUUID.fromList(uuids));
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.CONNECTOR, operation = Operation.FORCE_DELETE)
    public List<BulkActionMessageDto> forceDeleteConnector(@LogResource(uuid = true) List<String> uuids) throws NotFoundException, ValidationException {
        return connectorService.forceDeleteConnector(SecuredUUID.fromList(uuids));
    }
}
