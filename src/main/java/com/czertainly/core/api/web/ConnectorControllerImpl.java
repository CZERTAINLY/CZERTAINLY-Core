package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
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
import com.czertainly.core.auth.AuthEndpoint;
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

    @Autowired
    private ConnectorService connectorService;

    @InitBinder
    public void initBinder(final WebDataBinder webdataBinder) {
        webdataBinder.registerCustomEditor(FunctionGroupCode.class, new FunctionGroupCodeConverter());
        webdataBinder.registerCustomEditor(ConnectorStatusConverter.class, new ConnectorStatusConverter());
        webdataBinder.registerCustomEditor(Optional.class, new OptionalEnumConverter());
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CONNECTOR)
    public List<ConnectorDto> listConnectors(
            @RequestParam Optional<FunctionGroupCode> functionGroup,
            @RequestParam Optional<String> kind,
            @RequestParam Optional<ConnectorStatus> status) throws NotFoundException {
        return connectorService.listConnectors(SecurityFilter.create(), functionGroup, kind, status);
    }

    @Override
    public void bulkApprove(List<String> uuids) throws NotFoundException, ValidationException {
        connectorService.approve(SecuredUUID.fromList(uuids));
    }

    @Override
    public void bulkReconnect(List<String> uuids) throws ValidationException, NotFoundException, ConnectorException {
        connectorService.reconnect(SecuredUUID.fromList(uuids));
    }

    @Override
    public ConnectorDto getConnector(@PathVariable String uuid) throws NotFoundException, ConnectorException {
        return connectorService.getConnector(SecuredUUID.fromString(uuid));
    }

    @Override
    public ResponseEntity<?> createConnector(@RequestBody ConnectorRequestDto request)
            throws AlreadyExistException, ConnectorException {
        ConnectorDto connectorDto = connectorService.createConnector(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}")
                .buildAndExpand(connectorDto.getUuid()).toUri();
        UuidDto dto = new UuidDto();
        dto.setUuid(connectorDto.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    public ConnectorDto editConnector(@PathVariable String uuid, @RequestBody ConnectorUpdateRequestDto request)
            throws ConnectorException {
        return connectorService.editConnector(SecuredUUID.fromString(uuid), request);
    }

    @Override
    public void deleteConnector(@PathVariable String uuid) throws NotFoundException {
        connectorService.deleteConnector(SecuredUUID.fromString(uuid));
    }

    @Override
    public List<ConnectDto> connect(@RequestBody ConnectRequestDto request) throws ValidationException, ConnectorException {
        return connectorService.connect(request);
    }

    @Override
    public List<ConnectDto> reconnect(@PathVariable String uuid) throws ValidationException, NotFoundException, ConnectorException {
        return connectorService.reconnect(SecuredUUID.fromString(uuid));
    }

    @Override
    public void approve(@PathVariable String uuid) throws NotFoundException, ValidationException {
        connectorService.approve(SecuredUUID.fromString(uuid));
    }

    @Override
    public HealthDto checkHealth(@PathVariable String uuid) throws NotFoundException, ValidationException, ConnectorException {
        return connectorService.checkHealth(SecuredUUID.fromString(uuid));
    }

    @Override
    public List<BaseAttribute> getAttributes(@PathVariable String uuid,
                                             @PathVariable FunctionGroupCode functionGroup,
                                             @PathVariable String kind) throws NotFoundException, ConnectorException {
        return connectorService.getAttributes(SecuredUUID.fromString(uuid), functionGroup, kind);
    }

    @Override
    public void validateAttributes(@PathVariable String uuid,
                                      @PathVariable String functionGroup,
                                      @PathVariable String kind,
                                      @RequestBody List<RequestAttributeDto> attributes)
            throws NotFoundException, ConnectorException {
        connectorService.validateAttributes(SecuredUUID.fromString(uuid), FunctionGroupCode.findByCode(functionGroup), attributes,
                kind);
    }

	@Override
	public Map<FunctionGroupCode, Map<String, List<BaseAttribute>>> getAttributesAll(String uuid) throws NotFoundException, ConnectorException {
		return connectorService.getAllAttributesOfConnector(SecuredUUID.fromString(uuid));
	}

    @Override
    public List<BulkActionMessageDto> bulkDeleteConnector(List<String> uuids) throws NotFoundException, ValidationException, ConnectorException {
        return connectorService.bulkDeleteConnector(SecuredUUID.fromList(uuids));
    }

    @Override
    public List<BulkActionMessageDto> forceDeleteConnector(List<String> uuids) throws NotFoundException, ValidationException {
        return connectorService.forceDeleteConnector(SecuredUUID.fromList(uuids));
    }
}
