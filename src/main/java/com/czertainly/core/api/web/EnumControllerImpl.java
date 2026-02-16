package com.czertainly.core.api.web;

import com.czertainly.api.clients.v2.HealthApiClient;
import com.czertainly.api.clients.v2.InfoApiClient;
import com.czertainly.api.clients.v2.MetricsApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.interfaces.core.web.EnumController;
import com.czertainly.api.model.client.connector.v2.HealthInfo;
import com.czertainly.api.model.client.connector.v2.InfoResponse;
import com.czertainly.api.model.common.enums.PlatformEnum;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.enums.EnumItemDto;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.service.EnumService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class EnumControllerImpl implements EnumController {

    private EnumService enumService;

    @Autowired
    private MetricsApiClient metricsApiClient;
    @Autowired
    private InfoApiClient infoApiClient;
    @Autowired
    private HealthApiClient healthApiClient;
    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    public void setEnumService(EnumService enumService) {
        this.enumService = enumService;
    }

    @GetMapping(
            path = "/test-info",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public InfoResponse getInfoResponse() throws ConnectorException {
        ConnectorDto connectorDto = connectorRepository.findByName("Common-Credential-Provider").orElseThrow().mapToDto();
        return infoApiClient.getConnectorInfo(connectorDto);
    }

    @GetMapping(
            path = "/test-health",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public HealthInfo getHealthResponse() throws ConnectorException {
        ConnectorDto connectorDto = connectorRepository.findByName("Common-Credential-Provider").orElseThrow().mapToDto();
        return healthApiClient.checkHealth(connectorDto);
    }

    @GetMapping(
            path = "/test-metrics",
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public String getMetricsResponse() throws ConnectorException {
        ConnectorDto connectorDto = connectorRepository.findByName("Common-Credential-Provider").orElseThrow().mapToDto();

        return metricsApiClient.getMetrics(connectorDto);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.PLATFORM_ENUM, operation = Operation.LIST)
    public Map<PlatformEnum, Map<String, EnumItemDto>> getPlatformEnums() {
        return enumService.getPlatformEnums();
    }
}
