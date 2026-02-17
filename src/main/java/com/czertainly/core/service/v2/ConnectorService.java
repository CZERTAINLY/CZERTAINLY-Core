package com.czertainly.core.service.v2;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.connector.ConnectRequestDto;
import com.czertainly.api.model.client.connector.v2.ConnectorInfo;
import com.czertainly.api.model.client.connector.v2.HealthInfo;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.connector.v2.*;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.ResourceExtensionService;

import java.util.List;
import java.util.UUID;

public interface ConnectorService extends ResourceExtensionService {

    PaginationResponseDto<ConnectorDto> listConnectors(SecurityFilter filter, SearchRequestDto request);

    ConnectorDetailDto getConnector(SecuredUUID uuid) throws NotFoundException;

    ConnectorDetailDto createConnector(ConnectorRequestDto request);

    ConnectorDetailDto editConnector(SecuredUUID uuid, ConnectorUpdateRequestDto request);

    void deleteConnector(SecuredUUID uuid) throws NotFoundException;

    List<ConnectInfo> connect(ConnectRequestDto request);

    ConnectInfo reconnect(SecuredUUID uuid);

    void approve(SecuredUUID uuid) throws NotFoundException;

    void bulkApprove(List<SecuredUUID> uuids);

    void bulkReconnect(List<SecuredUUID> uuids);

    List<BulkActionMessageDto> bulkDeleteConnector(List<SecuredUUID> uuids);

    List<BulkActionMessageDto> forceDeleteConnector(List<SecuredUUID> uuids);

    HealthInfo checkHealth(SecuredUUID uuid) throws NotFoundException, ConnectorException;

    ConnectorInfo getInfo(SecuredUUID uuid) throws NotFoundException, ConnectorException;

}
