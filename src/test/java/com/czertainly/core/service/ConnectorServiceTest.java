package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.connector.ConnectorRequestDto;
import com.czertainly.api.model.client.connector.ConnectorUpdateRequestDto;
import com.czertainly.api.model.common.HealthDto;
import com.czertainly.api.model.common.HealthStatus;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.Connector2FunctionGroup;
import com.czertainly.core.dao.entity.FunctionGroup;
import com.czertainly.core.dao.repository.Connector2FunctionGroupRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.FunctionGroupRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.MetaDefinitions;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ConnectorServiceTest extends BaseSpringBootTest {

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
    public void testListConnectors() throws NotFoundException {
        List<ConnectorDto> connectors = connectorService.listConnectors(SecurityFilter.create(), null, null, null);
        Assertions.assertNotNull(connectors);
        Assertions.assertFalse(connectors.isEmpty());
        Assertions.assertEquals(1, connectors.size());
        Assertions.assertEquals(connector.getUuid().toString(), connectors.get(0).getUuid());
    }

    @Test
    public void testListConnectorsByFunctionGroup() throws NotFoundException {
        List<ConnectorDto> connectors = connectorService.listConnectors(
                SecurityFilter.create(),
                Optional.of(FunctionGroupCode.CREDENTIAL_PROVIDER), null, null
        );
        Assertions.assertNotNull(connectors);
        Assertions.assertFalse(connectors.isEmpty());
        Assertions.assertEquals(1, connectors.size());
        Assertions.assertEquals(connector.getUuid().toString(), connectors.get(0).getUuid());
    }

    @Test
    public void testListConnectorsByFunctionGroup_notFound() throws NotFoundException {
        List<ConnectorDto> connectors = connectorService.listConnectors(
                SecurityFilter.create(),
                Optional.of(FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER),
                null, null
        );
        Assertions.assertNotNull(connectors);
        Assertions.assertTrue(connectors.isEmpty());
    }

    @Test
    public void testListConnectorsByFunctionGroupAndKind() throws NotFoundException {
        List<ConnectorDto> connectors = connectorService.listConnectors(
                SecurityFilter.create(),
                Optional.of(FunctionGroupCode.CREDENTIAL_PROVIDER), Optional.of("ApiKey"),
                null
        );
        Assertions.assertNotNull(connectors);
        Assertions.assertFalse(connectors.isEmpty());
        Assertions.assertEquals(1, connectors.size());
        Assertions.assertEquals(connector.getUuid().toString(), connectors.get(0).getUuid());
    }

    @Test
    public void testListConnectorsByFunctionGroupAndKind_notFound() throws NotFoundException {
        Assertions.assertEquals(0,
                connectorService.listConnectors(
                        SecurityFilter.create(),
                        Optional.of(FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER),
                        Optional.of("wrong-kind"), null).size()
        );

    }

    @Test
    public void testListConnectorsByFunctionGroupAndKind_noConnectorOfKind() throws NotFoundException {
        List<ConnectorDto> connectors = connectorService.listConnectors(
                SecurityFilter.create(),
                Optional.of(FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER),
                Optional.of("wrong-kind"), null
        );
        Assertions.assertNotNull(connectors);
        Assertions.assertTrue(connectors.isEmpty());
    }

    @Test
    public void testGetAllAttributesOfConnector() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/credentialProvider/[^/]+/attributes"))
                .willReturn(WireMock.okJson("[]")));

        Map<FunctionGroupCode, Map<String, List<BaseAttribute>>> result = connectorService.getAllAttributesOfConnector(connector.getSecuredUuid());
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        Assertions.assertNotNull(result.get(FunctionGroupCode.CREDENTIAL_PROVIDER));
    }

    @Test
    public void testGetAllAttributesOfConnector_notFund() {
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.getAllAttributesOfConnector(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testGetConnector() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get("/v1")
                .willReturn(WireMock.okJson("[]")));

        ConnectorDto dto = connectorService.getConnector(connector.getSecuredUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(connector.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals(connector.getName(), dto.getName());
    }

    @Test
    public void testGetConnector_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.getConnector(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
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

        ConnectorUpdateRequestDto request = new ConnectorUpdateRequestDto();
        request.setUrl("http://localhost:3665");

        ConnectorDto dto = connectorService.editConnector(connector.getSecuredUuid(), request);
        Assertions.assertNotNull(dto);
    }

    @Test
    public void testEditConnector_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.editConnector(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), new ConnectorUpdateRequestDto()));
    }

    @Test
    public void testRemoveConnector() throws NotFoundException {
        connectorService.deleteConnector(connector.getSecuredUuid());
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.getConnector(connector.getSecuredUuid()));
    }

    @Test
    public void testRemoveConnector_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.deleteConnector(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testBulkRemove() throws NotFoundException {
        connectorService.bulkDeleteConnector(List.of(connector.getSecuredUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.getConnector(connector.getSecuredUuid()));
    }

    @Test
    public void testApproveConnector() throws NotFoundException {
        Connector waitingConnector = new Connector();
        waitingConnector.setStatus(ConnectorStatus.WAITING_FOR_APPROVAL);
        waitingConnector = connectorRepository.save(waitingConnector);

        connectorService.approve(waitingConnector.getSecuredUuid());

        Assertions.assertEquals(ConnectorStatus.CONNECTED, waitingConnector.getStatus());
    }

    @Test
    public void testApproveConnector_ValidationFail() {
        Assertions.assertThrows(ValidationException.class, () -> connectorService.approve(connector.getSecuredUuid()));
    }

    @Test
    public void testApproveConnector_notFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> connectorService.approve(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"))
        );
    }

    @Test
    public void testCheckHealth() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get("/v1/health")
                .willReturn(WireMock.okJson("{ \"status\": \"ok\" }")));

        HealthDto health = connectorService.checkHealth(connector.getSecuredUuid());
        Assertions.assertNotNull(health);
        Assertions.assertNotNull(health.getStatus());
        Assertions.assertEquals(HealthStatus.OK, health.getStatus());
    }

    @Test
    public void testCheckHealth_notFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> connectorService.checkHealth(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"))
        );
    }

    @Test
    public void testGetAttributes() throws ConnectorException {
        FunctionGroupCode code = FunctionGroupCode.CREDENTIAL_PROVIDER;
        String kind = "ApiKey";

        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/" + code.getCode() + "/" + kind + "/attributes"))
                .willReturn(WireMock.okJson("[]")));

        List<BaseAttribute> attributes = connectorService.getAttributes(connector.getSecuredUuid(), code, kind);
        Assertions.assertNotNull(attributes);
    }

    @Test
    public void testGetAttributes_validationFail() {
        Assertions.assertThrows(
                ValidationException.class,
                () -> connectorService.getAttributes(
                        connector.getSecuredUuid(),
                        FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER,
                        null
                )
        );
    }

    @Test
    public void testGetAttributes_notFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> connectorService.getAttributes(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"),
                        null,
                        null
                )
        );
    }

    @Test
    public void testValidateAttributes() throws ConnectorException {
        FunctionGroupCode code = FunctionGroupCode.CREDENTIAL_PROVIDER;
        String kind = "ApiKey";

        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/" + code.getCode() + "/" + kind + "/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        connectorService.validateAttributes(connector.getSecuredUuid(), code, List.of(), kind);

    }

    @Test
    public void testValidateAttributes_validationFail() {
        Assertions.assertThrows(ValidationException.class,
                () -> connectorService.validateAttributes(
                        connector.getSecuredUuid(),
                        FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER,
                        null,
                        null
                )
        );
    }

    @Test
    public void testValidateAttributes_validationFailOnConnector() throws ConnectorException {
        FunctionGroupCode code = FunctionGroupCode.CREDENTIAL_PROVIDER;
        String kind = "ApiKey";

        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/" + code.getCode() + "/" + kind + "/attributes/validate"))
                .willReturn(WireMock.okJson("false")
                )
        );

        connectorService.validateAttributes(connector.getSecuredUuid(), code, List.of(), kind);
    }

    @Test
    public void testValidateAttributes_validationFailOnConnectorException() {
        FunctionGroupCode code = FunctionGroupCode.CREDENTIAL_PROVIDER;
        String kind = "ApiKey";

        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/" + code.getCode() + "/" + kind + "/attributes/validate"))
                .willReturn(WireMock.aResponse()
                        .withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[ \"Validation failed\" ]")
                )
        );

        Assertions.assertThrows(ValidationException.class,
                () -> connectorService.validateAttributes(connector.getSecuredUuid(), code, List.of(), kind));
    }

    @Test
    public void testValidateAttributes_notFound() {
        Assertions.assertThrows(NotFoundException.class,
                () -> connectorService.validateAttributes(
                        SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"),
                        null,
                        null,
                        null
                )
        );
    }
}
