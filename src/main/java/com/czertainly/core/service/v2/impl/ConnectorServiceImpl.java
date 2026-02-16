package com.czertainly.core.service.v2.impl;

import com.czertainly.api.clients.v2.HealthApiClient;
import com.czertainly.api.clients.v2.InfoApiClient;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.connector.ConnectRequestDto;
import com.czertainly.api.model.client.connector.v2.ConnectorInfo;
import com.czertainly.api.model.client.connector.v2.HealthInfo;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.v2.*;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.v2.ConnectorService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service(Resource.Codes.CONNECTOR)
@Transactional
public class ConnectorServiceImpl implements ConnectorService {

    private InfoApiClient infoApiClient;
    private HealthApiClient healthApiClient;
    private ConnectorRepository connectorRepository;

    @Autowired
    public void setInfoApiClient(InfoApiClient infoApiClient) {
        this.infoApiClient = infoApiClient;
    }

    @Autowired
    public void setHealthApiClient(HealthApiClient healthApiClient) {
        this.healthApiClient = healthApiClient;
    }

    @Autowired
    public void setConnectorRepository(ConnectorRepository connectorRepository) {
        this.connectorRepository = connectorRepository;
    }

    @Override
    public NameAndUuidDto getResourceObject(UUID objectUuid) throws NotFoundException {
        return null;
    }

    @Override
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter, List<SearchFilterRequestDto> filters, PaginationRequestDto pagination) {
        return List.of();
    }

    @Override
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {

    }

    @Override
    public PaginationResponseDto<ConnectorDto> listConnectors(SecurityFilter filter, SearchRequestDto request) {
        return null;
    }

    @Override
    public ConnectorDetailDto getConnector(SecuredUUID uuid) {
        return null;
    }

    @Override
    public ConnectorDetailDto createConnector(ConnectorRequestDto request) {
        return null;
    }

    @Override
    public ConnectorDetailDto editConnector(SecuredUUID uuid, ConnectorUpdateRequestDto request) {
        return null;
    }

    @Override
    public void deleteConnector(SecuredUUID uuid) {

    }

    @Override
    public List<ConnectInfo> connect(ConnectRequestDto request) {
        return List.of();
    }

    @Override
    public ConnectInfo reconnect(SecuredUUID uuid) {
        return null;
    }

    @Override
    public void approve(SecuredUUID uuid) {

    }

    @Override
    public void bulkApprove(List<SecuredUUID> uuids) {

    }

    @Override
    public void bulkReconnect(List<SecuredUUID> uuids) {

    }

    @Override
    public List<BulkActionMessageDto> bulkDeleteConnector(List<SecuredUUID> uuids) {
        return List.of();
    }

    @Override
    public List<BulkActionMessageDto> forceDeleteConnector(List<SecuredUUID> uuids) {
        return List.of();
    }

    @Override
    public HealthInfo checkHealth(SecuredUUID uuid) throws NotFoundException {
        ConnectorDto connector = connectorRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid))
                .mapToDto();

        return healthApiClient.checkHealth(connector);
    }

    @Override
    public ConnectorInfo getInfo(SecuredUUID uuid) {
        return null;
    }


}
