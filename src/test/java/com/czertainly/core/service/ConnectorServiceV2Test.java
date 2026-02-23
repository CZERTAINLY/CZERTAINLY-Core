package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.connector.v2.*;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.connector.AuthType;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.v2.ConnectInfo;
import com.czertainly.api.model.core.connector.v2.ConnectorDetailDto;
import com.czertainly.api.model.core.connector.v2.ConnectorRequestDto;
import com.czertainly.api.model.core.connector.v2.ConnectorUpdateRequestDto;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.ConnectorInterfaceEntity;
import com.czertainly.core.dao.repository.ConnectorInterfaceRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.v2.ConnectorService;
import com.czertainly.core.util.BaseSpringBootTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

class ConnectorServiceV2Test extends BaseSpringBootTest {

    private static final String CONNECTOR_NAME = "testConnectorV2";

    @Autowired
    private ConnectorService connectorService;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private ConnectorInterfaceRepository connectorInterfaceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Connector connector;
    private WireMockServer mockServer;

    @BeforeEach
    void setUp() {
        mockServer = new WireMockServer(0);
        mockServer.start();
        WireMock.configureFor("localhost", mockServer.port());

        connector = new Connector();
        connector.setName(CONNECTOR_NAME);
        connector.setUrl("http://localhost:" + mockServer.port());
        connector.setVersion(ConnectorVersion.V2);
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector.setAuthType(AuthType.NONE);
        connector = connectorRepository.save(connector);

        // Add connector interface
        ConnectorInterfaceEntity interfaceEntity = new ConnectorInterfaceEntity();
        interfaceEntity.setConnectorUuid(connector.getUuid());
        interfaceEntity.setInterfaceCode(ConnectorInterface.AUTHORITY);
        interfaceEntity.setVersion("v2");
        interfaceEntity.setFeatures(List.of(FeatureFlag.STATELESS));
        connectorInterfaceRepository.save(interfaceEntity);
    }

    @AfterEach
    void tearDown() {
        mockServer.stop();
    }

    @Test
    void testGetConnector() throws NotFoundException, ConnectorException {
        ConnectorDetailDto dto = connectorService.getConnector(connector.getSecuredUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(connector.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals(connector.getName(), dto.getName());
        Assertions.assertEquals(ConnectorVersion.V2, dto.getVersion());
    }

    @Test
    void testGetConnector_notFound() {
        Assertions.assertThrows(NotFoundException.class,
                () -> connectorService.getConnector(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testCreateConnector() throws ConnectorException, AlreadyExistException, AttributeException, NotFoundException, JsonProcessingException {
        ConnectorRequestDto request = new ConnectorRequestDto();
        request.setName("newConnectorV2");
        request.setUrl("http://localhost:" + mockServer.port());
        request.setVersion(ConnectorVersion.V2);
        request.setAuthType(AuthType.NONE);
        Assertions.assertThrows(AlreadyExistException.class, () -> connectorService.createConnector(request));

        mockServer.stop();
        mockServer = new WireMockServer(0);
        mockServer.start();
        request.setUrl("http://localhost:" + mockServer.port());

        mockInfoEndpoint();

        ConnectorDetailDto dto = connectorService.createConnector(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
        Assertions.assertEquals(ConnectorVersion.V2, dto.getVersion());
    }

    @Test
    void testCreateConnector_validationFail() {
        ConnectorRequestDto request = new ConnectorRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> connectorService.createConnector(request));
    }

    @Test
    void testCreateConnector_alreadyExist() {
        ConnectorRequestDto request = new ConnectorRequestDto();
        request.setName(CONNECTOR_NAME);
        request.setUrl("http://localhost:" + mockServer.port());
        request.setVersion(ConnectorVersion.V2);
        request.setAuthType(AuthType.NONE);
        Assertions.assertThrows(AlreadyExistException.class, () -> connectorService.createConnector(request));
    }

    @Test
    void testEditConnector() throws ConnectorException, AttributeException, NotFoundException, JsonProcessingException {
        mockInfoEndpoint();

        ConnectorUpdateRequestDto request = new ConnectorUpdateRequestDto();
        request.setUrl("http://localhost:" + mockServer.port());
        request.setAuthType(AuthType.NONE);

        ConnectorDetailDto dto = connectorService.editConnector(connector.getSecuredUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(connector.getName(), dto.getName());
    }

    @Test
    void testEditConnector_notFound() {
        Assertions.assertThrows(NotFoundException.class,
                () -> connectorService.editConnector(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), new ConnectorUpdateRequestDto()));
    }

    @Test
    void testDeleteConnector() throws NotFoundException {
        connectorService.deleteConnector(connector.getSecuredUuid());
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.getConnector(connector.getSecuredUuid()));
    }

    @Test
    void testDeleteConnector_notFound() {
        Assertions.assertThrows(NotFoundException.class,
                () -> connectorService.deleteConnector(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testBulkDeleteConnector() {
        connectorService.bulkDeleteConnector(List.of(connector.getSecuredUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.getConnector(connector.getSecuredUuid()));
    }

    @Test
    void testReconnectConnector() throws NotFoundException, ConnectorException, JsonProcessingException {
        mockInfoEndpoint();

        ConnectInfo connectInfo = connectorService.reconnect(connector.getSecuredUuid());
        Assertions.assertNotNull(connectInfo);
        Assertions.assertEquals(ConnectorVersion.V2, connectInfo.getVersion());
    }

    @Test
    void testReconnectConnector_notFound() {
        Assertions.assertThrows(NotFoundException.class,
                () -> connectorService.reconnect(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testReconnectConnector_communicationError() {
        mockServer.stubFor(WireMock.get("/v2/info")
                .willReturn(WireMock.aResponse().withStatus(500).withBody("Internal Server Error")));

        SecuredUUID connectorUuid = connector.getSecuredUuid();
        Assertions.assertThrows(ConnectorException.class, () -> connectorService.reconnect(connectorUuid));
    }

    @Test
    void testCheckHealth() throws ConnectorException, NotFoundException {
        mockServer.stubFor(WireMock.get("/v2/health")
                .willReturn(WireMock.okJson("{ \"status\": \"UP\" }")));

        HealthInfo health = connectorService.checkHealth(connector.getSecuredUuid());
        Assertions.assertNotNull(health);
        Assertions.assertEquals(HealthStatus.UP, health.getStatus());
    }

    @Test
    void testCheckHealth_notFound() {
        Assertions.assertThrows(NotFoundException.class,
                () -> connectorService.checkHealth(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testGetInfo() throws ConnectorException, NotFoundException, JsonProcessingException {
        mockInfoEndpoint();

        ConnectorInfo info = connectorService.getInfo(connector.getSecuredUuid());
        Assertions.assertNotNull(info);
    }

    @Test
    void testApproveConnector() throws NotFoundException {
        Connector waitingConnector = new Connector();
        waitingConnector.setName("waitingConnector");
        waitingConnector.setUrl("http://localhost:" + mockServer.port());
        waitingConnector.setVersion(ConnectorVersion.V2);
        waitingConnector.setStatus(ConnectorStatus.WAITING_FOR_APPROVAL);
        waitingConnector = connectorRepository.save(waitingConnector);

        connectorService.approve(waitingConnector.getSecuredUuid());

        Connector approvedConnector = connectorRepository.findByUuid(waitingConnector.getUuid()).orElseThrow();
        Assertions.assertEquals(ConnectorStatus.CONNECTED, approvedConnector.getStatus());
    }

    @Test
    void testApproveConnector_validationFail() {
        // Connector is already CONNECTED, not WAITING_FOR_APPROVAL
        SecuredUUID connectorUuid = connector.getSecuredUuid();
        Assertions.assertThrows(ValidationException.class, () -> connectorService.approve(connectorUuid));
    }

    @Test
    void testApproveConnector_notFound() {
        Assertions.assertThrows(NotFoundException.class,
                () -> connectorService.approve(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testConnect() throws JsonProcessingException {
        mockInfoEndpoint();

        // Mock V1 endpoint to return error (no V1 connector)
        mockServer.stubFor(WireMock.get("/v1")
                .willReturn(WireMock.aResponse().withStatus(404).withBody("Not Found")));

        var request = new com.czertainly.api.model.client.connector.ConnectRequestDto();
        request.setUrl("http://localhost:" + mockServer.port());
        request.setAuthType(AuthType.NONE);

        List<ConnectInfo> connectInfos = connectorService.connect(request);
        Assertions.assertNotNull(connectInfos);
        Assertions.assertFalse(connectInfos.isEmpty());
    }

    @Test
    void testListResourceObjects() {
        List<NameAndUuidDto> dtos = connectorService.listResourceObjects(SecurityFilter.create(), null, null);
        Assertions.assertFalse(dtos.isEmpty());
        Assertions.assertTrue(dtos.stream().anyMatch(dto -> dto.getName().equals(CONNECTOR_NAME)));
    }

    private void mockInfoEndpoint() throws JsonProcessingException {
        List<ConnectorInterfaceInfo> connectorInterfaceInfos = createMockInterfaceInfos();

        ConnectorInfo connectorInfo = new ConnectorInfo();
        connectorInfo.setName(CONNECTOR_NAME);
        connectorInfo.setId("czertainly.common.credential.provider");
        connectorInfo.setVersion("1.0.0");

        InfoResponse infoResponse = new InfoResponse();
        infoResponse.setConnector(connectorInfo);
        infoResponse.setInterfaces(connectorInterfaceInfos);
        String jsonBody = objectMapper.writeValueAsString(infoResponse);

        mockServer.stubFor(WireMock.get("/v2/info")
                .willReturn(WireMock.okJson(jsonBody)));
    }

    private List<ConnectorInterfaceInfo> createMockInterfaceInfos() {
        List<ConnectorInterfaceInfo> connectorInterfaceInfos = new ArrayList<>();
        List<ConnectorInterface> interfaces = List.of(
                ConnectorInterface.INFO,
                ConnectorInterface.HEALTH,
                ConnectorInterface.METRICS,
                ConnectorInterface.AUTHORITY
        );

        for (ConnectorInterface connectorInterface : interfaces) {
            ConnectorInterfaceInfo info = new ConnectorInterfaceInfo();
            info.setCode(connectorInterface);
            info.setVersion("v2");
            info.setFeatures(List.of());
            connectorInterfaceInfos.add(info);
        }
        return connectorInterfaceInfos;
    }
}
