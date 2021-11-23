package com.czertainly.core.api.web;

import java.net.ConnectException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.model.AttributeCallback;
import com.czertainly.api.model.HealthDto;
import com.czertainly.api.model.connector.ForceDeleteMessageDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.czertainly.core.service.ConnectorService;
import com.czertainly.api.core.interfaces.web.ConnectorController;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.AttributeDefinition;
import com.czertainly.api.model.connector.ConnectDto;
import com.czertainly.api.model.connector.ConnectorDto;
import com.czertainly.api.model.connector.FunctionGroupCode;

@RestController
public class ConnectorControllerImpl implements ConnectorController {

    @Autowired
    private ConnectorService connectorService;

    @Override
    public List<ConnectorDto> listConnectors() {
        return connectorService.listConnectors();
    }

    @Override
    public List<ConnectorDto> listConnectors(
            @RequestParam FunctionGroupCode functionGroup,
            @RequestParam String kind) throws NotFoundException {
        return connectorService.listConnectors(functionGroup, kind);
    }

    @Override
    public List<ConnectorDto> listConnectorsByFunctionGroup(
            @RequestParam FunctionGroupCode functionGroup) throws NotFoundException {
        return connectorService.listConnectorsByFunctionGroup(functionGroup);
    }

    @Override
    public void bulkApprove(List<String> uuids) throws NotFoundException, ValidationException {
        connectorService.approve(uuids);
    }

    @Override
    public void bulkReconnect(List<String> uuids) throws ValidationException, NotFoundException, ConnectorException {
        connectorService.reconnect(uuids);
    }

    @Override
    public ConnectorDto getConnector(@PathVariable String uuid) throws NotFoundException, ConnectorException {
        return connectorService.getConnector(uuid);
    }

    @Override
    public ResponseEntity<?> createConnector(@RequestBody ConnectorDto request)
            throws AlreadyExistException, NotFoundException {
        ConnectorDto connectorDto = connectorService.createConnector(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}")
                .buildAndExpand(connectorDto.getUuid()).toUri();

        return ResponseEntity.created(location).build();
    }

    @Override
    public ConnectorDto updateConnector(@PathVariable String uuid, @RequestBody ConnectorDto request)
            throws NotFoundException {
        return connectorService.updateConnector(uuid, request);
    }

    @Override
    public void removeConnector(@PathVariable String uuid) throws NotFoundException {
        connectorService.removeConnector(uuid);
    }

    @Override
    public List<ConnectDto> connect(@RequestBody ConnectorDto request) throws ValidationException, ConnectorException {
        return connectorService.connect(request);
    }

    @Override
    public List<ConnectDto> reconnect(@PathVariable String uuid) throws ValidationException, NotFoundException, ConnectorException {
        return connectorService.reconnect(uuid);
    }

    @Override
    public void approve(@PathVariable String uuid) throws NotFoundException, ValidationException {
        connectorService.approve(uuid);
    }

    @Override
    public HealthDto checkHealth(@PathVariable String uuid) throws NotFoundException, ValidationException, ConnectorException {
        return connectorService.checkHealth(uuid);
    }

    @Override
    public List<AttributeDefinition> getAttributes(@PathVariable String uuid,
                                                   @PathVariable String functionGroup,
                                                   @PathVariable String functionGroupKind) throws NotFoundException, ConnectorException {
        return connectorService.getAttributes(uuid, FunctionGroupCode.findByCode(functionGroup), functionGroupKind);
    }

    @Override
    public boolean validateAttributes(@PathVariable String uuid,
                                      @PathVariable String functionGroup,
                                      @PathVariable String functionGroupKind,
                                      @RequestBody List<AttributeDefinition> attributes)
            throws NotFoundException, ConnectorException {
        return connectorService.validateAttributes(uuid, FunctionGroupCode.findByCode(functionGroup), attributes,
                functionGroupKind);
    }

	@Override
	public Map<FunctionGroupCode, Map<String, List<AttributeDefinition>>> getAttributesAll(String uuid) throws NotFoundException, ConnectorException {
		return connectorService.getAllAttributesOfConnector(uuid);
	}

    @Override
    public Object callback(@PathVariable String uuid, @RequestBody AttributeCallback callback) throws NotFoundException, ConnectorException, ValidationException {
        return connectorService.callback(uuid, callback);
    }

    @Override
    public List<ForceDeleteMessageDto> bulkRemoveConnector(List<String> uuids) throws NotFoundException, ValidationException, ConnectorException {
        return connectorService.bulkRemoveConnector(uuids);
    }

    @Override
    public void bulkForceRemoveConnector(List<String> uuids) throws NotFoundException, ValidationException {
        connectorService.bulkForceRemoveConnector(uuids);
    }
}
