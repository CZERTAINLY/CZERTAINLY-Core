package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.connector.ConnectorRequestDto;
import com.czertainly.api.model.common.AttributeDefinition;
import com.czertainly.api.model.common.HealthDto;
import com.czertainly.api.model.common.HealthStatus;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.Connector2FunctionGroup;
import com.czertainly.core.dao.entity.FunctionGroup;
import com.czertainly.core.dao.repository.Connector2FunctionGroupRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.FunctionGroupRepository;
import com.czertainly.core.util.MetaDefinitions;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@SpringBootTest
@Transactional
@Rollback
@WithMockUser(roles="SUPERADMINISTRATOR")
public class ConnectorServiceTest {

    private static final String CONNECTOR_NAME = "testConnector1";

    @Autowired
    private ConnectorService connectorService;

    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private FunctionGroupRepository functionGroupRepository;
    @Autowired
    private Connector2FunctionGroupRepository connector2FunctionGroupRepository;

    private Connector connector;

    private WireMockServer mockServer;

    @BeforeEach
    public void setUp() {
        mockServer = new WireMockServer(3665);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        connector = new Connector();
        connector.setName(CONNECTOR_NAME);
        connector.setUrl("http://localhost:3665");
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        FunctionGroup functionGroup = new FunctionGroup();
        functionGroup.setCode(FunctionGroupCode.CREDENTIAL_PROVIDER);
        functionGroup.setName(FunctionGroupCode.CREDENTIAL_PROVIDER.getCode());
        functionGroupRepository.save(functionGroup);

        Connector2FunctionGroup c2fg = new Connector2FunctionGroup();
        c2fg.setConnector(connector);
        c2fg.setFunctionGroup(functionGroup);
        c2fg.setKinds(MetaDefinitions.serializeArrayString(List.of("ApiKey")));
        connector2FunctionGroupRepository.save(c2fg);

        connector.getFunctionGroups().add(c2fg);
        connectorRepository.save(connector);
    }

    @AfterEach
    public void tearDown() {
        mockServer.stop();
    }

    @Test
    public void testListConnectors() {
        List<ConnectorDto> connectors = connectorService.listConnectors();
        Assertions.assertNotNull(connectors);
        Assertions.assertFalse(connectors.isEmpty());
        Assertions.assertEquals(1, connectors.size());
        Assertions.assertEquals(connector.getUuid(), connectors.get(0).getUuid());
    }

    @Test
    public void testListConnectorsByFunctionGroup() throws NotFoundException {
        List<ConnectorDto> connectors = connectorService.listConnectorsByFunctionGroup(FunctionGroupCode.CREDENTIAL_PROVIDER);
        Assertions.assertNotNull(connectors);
        Assertions.assertFalse(connectors.isEmpty());
        Assertions.assertEquals(1, connectors.size());
        Assertions.assertEquals(connector.getUuid(), connectors.get(0).getUuid());
    }

    @Test
    public void testListConnectorsByFunctionGroup_notFound() {
        List<ConnectorDto> connectors = connectorService.listConnectorsByFunctionGroup(FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER);
        Assertions.assertNotNull(connectors);
        Assertions.assertTrue(connectors.isEmpty());
    }

    @Test
    public void testListConnectorsByFunctionGroupAndKind() throws NotFoundException {
        List<ConnectorDto> connectors = connectorService.listConnectors(FunctionGroupCode.CREDENTIAL_PROVIDER, "ApiKey");
        Assertions.assertNotNull(connectors);
        Assertions.assertFalse(connectors.isEmpty());
        Assertions.assertEquals(1, connectors.size());
        Assertions.assertEquals(connector.getUuid(), connectors.get(0).getUuid());
    }

    @Test
    public void testListConnectorsByFunctionGroupAndKind_notFound() {
        Assertions.assertThrows(NotFoundException.class, () ->
                connectorService.listConnectors(FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER, "wrong-kind"));

    }

    @Test
    public void testListConnectorsByFunctionGroupAndKind_noConnectorOfKind() throws NotFoundException {
        List<ConnectorDto> connectors = connectorService.listConnectors(FunctionGroupCode.CREDENTIAL_PROVIDER, "wrong-kind");
        Assertions.assertNotNull(connectors);
        Assertions.assertTrue(connectors.isEmpty());
    }

    @Test
    public void testGetAllAttributesOfConnector() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/credentialProvider/[^/]+/attributes"))
                .willReturn(WireMock.okJson("[]")));

        Map<FunctionGroupCode, Map<String, List<AttributeDefinition>>> result = connectorService.getAllAttributesOfConnector(connector.getUuid());
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        Assertions.assertNotNull(result.get(FunctionGroupCode.CREDENTIAL_PROVIDER));
    }

    @Test
    public void testGetAllAttributesOfConnector_notFund() {
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.getAllAttributesOfConnector("wrong-uuid"));
    }

    @Test
    public void testGetConnector() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get("/v1")
                .willReturn(WireMock.okJson("[]")));

        ConnectorDto dto = connectorService.getConnector(connector.getUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(connector.getUuid(), dto.getUuid());
        Assertions.assertEquals(connector.getName(), dto.getName());
    }

    @Test
    public void testGetConnector_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.getConnector("wrong-uuid"));
    }

    @Test
    public void testAddConnector() throws ConnectorException, AlreadyExistException {
        mockServer.stubFor(WireMock
                .get("/v1")
                .willReturn(WireMock.okJson("[]")));

        ConnectorRequestDto request = new ConnectorRequestDto();
        request.setName("testConnector2");
        request.setUrl("http://localhost:3665");

        ConnectorDto dto = connectorService.createConnector(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
    }

    @Test
    public void testAddConnector_validationFail() {
        ConnectorRequestDto request = new ConnectorRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> connectorService.createConnector(request));
    }

    @Test
    public void testAddConnector_alreadyExist() {
        ConnectorRequestDto request = new ConnectorRequestDto();
        request.setName(CONNECTOR_NAME);
        Assertions.assertThrows(AlreadyExistException.class, () -> connectorService.createConnector(request));
    }

    @Test
    public void testEditConnector() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get("/v1")
                .willReturn(WireMock.okJson("[]")));

        ConnectorRequestDto request = new ConnectorRequestDto();
        request.setName("testConnector2");
        request.setUrl("http://localhost:3665");

        ConnectorDto dto = connectorService.updateConnector(connector.getUuid(), request);
        Assertions.assertNotNull(dto);
    }

    @Test
    public void testEditConnector_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.updateConnector("wrong-uuid", new ConnectorRequestDto()));
    }

    @Test
    public void testRemoveConnector() throws NotFoundException {
        connectorService.removeConnector(connector.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.getConnector(connector.getUuid()));
    }

    @Test
    public void testRemoveConnector_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.removeConnector("wrong-uuid"));
    }

    @Test
    public void testBulkRemove() throws NotFoundException {
        connectorService.bulkRemoveConnector(List.of(connector.getUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.getConnector(connector.getUuid()));
    }

    @Test
    public void testApproveConnector() throws NotFoundException {
        Connector waitingConnector = new Connector();
        waitingConnector.setStatus(ConnectorStatus.WAITING_FOR_APPROVAL);
        waitingConnector = connectorRepository.save(waitingConnector);

        connectorService.approve(waitingConnector.getUuid());

        Assertions.assertEquals(ConnectorStatus.REGISTERED, waitingConnector.getStatus());
    }

    @Test
    public void testApproveConnector_ValidationFail() {
        Assertions.assertThrows(ValidationException.class, () -> connectorService.approve(connector.getUuid()));
    }

    @Test
    public void testApproveConnector_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.approve("wrong-uuid"));
    }

    @Test
    public void testCheckHealth() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get("/v1/health")
                .willReturn(WireMock.okJson("{ \"status\": \"OK\" }")));

        HealthDto health = connectorService.checkHealth(connector.getUuid());
        Assertions.assertNotNull(health);
        Assertions.assertNotNull(health.getStatus());
        Assertions.assertEquals(HealthStatus.OK, health.getStatus());
    }

    @Test
    public void testCheckHealth_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.checkHealth("wrong-uuid"));
    }

    @Test
    public void testGetAttributes() throws ConnectorException {
        FunctionGroupCode code = FunctionGroupCode.CREDENTIAL_PROVIDER;
        String kind = "ApiKey";

        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/"+code.getCode()+"/"+kind+"/attributes"))
                .willReturn(WireMock.okJson("[]")));

        List<AttributeDefinition> attributes = connectorService.getAttributes(connector.getUuid(), code, kind);
        Assertions.assertNotNull(attributes);
    }

    @Test
    public void testGetAttributes_validationFail() {
        Assertions.assertThrows(ValidationException.class, () -> connectorService.getAttributes(connector.getUuid(), FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER, null));
    }

    @Test
    public void testGetAttributes_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.getAttributes("wrong-uuid", null, null));
    }

    @Test
    public void testValidateAttributes() throws ConnectorException {
        FunctionGroupCode code = FunctionGroupCode.CREDENTIAL_PROVIDER;
        String kind = "ApiKey";

        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/"+code.getCode()+"/"+kind+"/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        boolean result = connectorService.validateAttributes(connector.getUuid(), code, List.of(), kind);
        Assertions.assertTrue(result);
    }

    @Test
    public void testValidateAttributes_validationFail() {
        Assertions.assertThrows(ValidationException.class,
                () -> connectorService.validateAttributes(connector.getUuid(), FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER, null, null));
    }

    @Test
    public void testValidateAttributes_validationFailOnConnector() throws ConnectorException {
        FunctionGroupCode code = FunctionGroupCode.CREDENTIAL_PROVIDER;
        String kind = "ApiKey";

        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/"+code.getCode()+"/"+kind+"/attributes/validate"))
                .willReturn(WireMock.okJson("false")));

        boolean result = connectorService.validateAttributes(connector.getUuid(), code, List.of(), kind);
        Assertions.assertFalse(result);
    }

    @Test
    public void testValidateAttributes_validationFailOnConnectorException() {
        FunctionGroupCode code = FunctionGroupCode.CREDENTIAL_PROVIDER;
        String kind = "ApiKey";

        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/"+code.getCode()+"/"+kind+"/attributes/validate"))
                .willReturn(WireMock.aResponse()
                        .withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[ \"Validation failed\" ]")));

        Assertions.assertThrows(ValidationException.class,
                () -> connectorService.validateAttributes(connector.getUuid(), code, List.of(), kind));
    }

    @Test
    public void testValidateAttributes_notFound() {
        Assertions.assertThrows(NotFoundException.class,
                () -> connectorService.validateAttributes("wrong-uuid", null, null, null));
    }
}
