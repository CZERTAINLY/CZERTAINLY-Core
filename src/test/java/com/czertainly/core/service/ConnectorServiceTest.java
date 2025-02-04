package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.connector.ConnectorRequestDto;
import com.czertainly.api.model.client.connector.ConnectorUpdateRequestDto;
import com.czertainly.api.model.common.HealthDto;
import com.czertainly.api.model.common.HealthStatus;
import com.czertainly.api.model.common.NameAndUuidDto;
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

class ConnectorServiceTest extends BaseSpringBootTest {

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
        mockServer = new WireMockServer(0);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        connector = new Connector();
        connector.setName(CONNECTOR_NAME);
        connector.setUrl("http://localhost:"+mockServer.port());
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
    void testListConnectors() throws NotFoundException {
        List<ConnectorDto> connectors = connectorService.listConnectors(SecurityFilter.create(), Optional.empty(), Optional.empty(), Optional.empty());
        Assertions.assertNotNull(connectors);
        Assertions.assertFalse(connectors.isEmpty());
        Assertions.assertEquals(1, connectors.size());
        Assertions.assertEquals(connector.getUuid().toString(), connectors.get(0).getUuid());
    }

    @Test
    void testListConnectorsByFunctionGroup() throws NotFoundException {
        List<ConnectorDto> connectors = connectorService.listConnectors(
                SecurityFilter.create(),
                Optional.of(FunctionGroupCode.CREDENTIAL_PROVIDER), Optional.empty(), Optional.empty()
        );
        Assertions.assertNotNull(connectors);
        Assertions.assertFalse(connectors.isEmpty());
        Assertions.assertEquals(1, connectors.size());
        Assertions.assertEquals(connector.getUuid().toString(), connectors.get(0).getUuid());
    }

    @Test
    void testListConnectorsByFunctionGroup_notFound() throws NotFoundException {
        List<ConnectorDto> connectors = connectorService.listConnectors(
                SecurityFilter.create(),
                Optional.of(FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER),
                Optional.empty(), Optional.empty()
        );
        Assertions.assertNotNull(connectors);
        Assertions.assertTrue(connectors.isEmpty());
    }

    @Test
    void testListConnectorsByFunctionGroupAndKind() throws NotFoundException {
        List<ConnectorDto> connectors = connectorService.listConnectors(
                SecurityFilter.create(),
                Optional.of(FunctionGroupCode.CREDENTIAL_PROVIDER), Optional.of("ApiKey"),
                Optional.empty()
        );
        Assertions.assertNotNull(connectors);
        Assertions.assertFalse(connectors.isEmpty());
        Assertions.assertEquals(1, connectors.size());
        Assertions.assertEquals(connector.getUuid().toString(), connectors.get(0).getUuid());
    }

    @Test
    void testListConnectorsByFunctionGroupAndKind_notFound() throws NotFoundException {
        Assertions.assertEquals(0,
                connectorService.listConnectors(
                        SecurityFilter.create(),
                        Optional.of(FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER),
                        Optional.of("wrong-kind"), Optional.empty()).size()
        );

    }

    @Test
    void testListConnectorsByFunctionGroupAndKind_noConnectorOfKind() throws NotFoundException {
        List<ConnectorDto> connectors = connectorService.listConnectors(
                SecurityFilter.create(),
                Optional.of(FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER),
                Optional.of("wrong-kind"), Optional.empty()
        );
        Assertions.assertNotNull(connectors);
        Assertions.assertTrue(connectors.isEmpty());
    }

    @Test
    void testGetAllAttributesOfConnector() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/credentialProvider/[^/]+/attributes"))
                .willReturn(WireMock.okJson("[]")));

        Map<FunctionGroupCode, Map<String, List<BaseAttribute>>> result = connectorService.getAllAttributesOfConnector(connector.getSecuredUuid());
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        Assertions.assertNotNull(result.get(FunctionGroupCode.CREDENTIAL_PROVIDER));
    }

    @Test
    void testGetAllAttributesOfConnector_notFund() {
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.getAllAttributesOfConnector(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testGetConnector() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get("/v1")
                .willReturn(WireMock.okJson("[]")));

        ConnectorDto dto = connectorService.getConnector(connector.getSecuredUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(connector.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals(connector.getName(), dto.getName());
    }

    @Test
    void testGetConnector_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.getConnector(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testAddConnector() throws ConnectorException, AlreadyExistException, AttributeException {
        mockServer.stubFor(WireMock
                .get("/v1")
                .willReturn(WireMock.okJson("[]")));

        ConnectorRequestDto request = new ConnectorRequestDto();
        request.setName("testConnector2");
        request.setUrl("http://localhost:"+mockServer.port());

        ConnectorDto dto = connectorService.createConnector(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
    }

    @Test
    void testAddConnector_validationFail() {
        ConnectorRequestDto request = new ConnectorRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> connectorService.createConnector(request));
    }

    @Test
    void testAddConnector_alreadyExist() {
        ConnectorRequestDto request = new ConnectorRequestDto();
        request.setName(CONNECTOR_NAME);
        Assertions.assertThrows(AlreadyExistException.class, () -> connectorService.createConnector(request));
    }

    @Test
    void testEditConnector() throws ConnectorException, AttributeException {
        mockServer.stubFor(WireMock
                .get("/v1")
                .willReturn(WireMock.okJson("[]")));

        ConnectorUpdateRequestDto request = new ConnectorUpdateRequestDto();
        request.setUrl("http://localhost:"+mockServer.port());

        ConnectorDto dto = connectorService.editConnector(connector.getSecuredUuid(), request);
        Assertions.assertNotNull(dto);
    }

    @Test
    void testEditConnector_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.editConnector(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), new ConnectorUpdateRequestDto()));
    }

    @Test
    void testRemoveConnector() throws NotFoundException {
        connectorService.deleteConnector(connector.getSecuredUuid());
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.getConnector(connector.getSecuredUuid()));
    }

    @Test
    void testRemoveConnector_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.deleteConnector(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testBulkRemove() throws NotFoundException {
        connectorService.bulkDeleteConnector(List.of(connector.getSecuredUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> connectorService.getConnector(connector.getSecuredUuid()));
    }

    @Test
    void testApproveConnector() throws ConnectorException {
        Connector waitingConnector = new Connector();
        waitingConnector.setStatus(ConnectorStatus.WAITING_FOR_APPROVAL);
        waitingConnector = connectorRepository.save(waitingConnector);

        connectorService.approve(waitingConnector.getSecuredUuid());

        mockServer.stubFor(WireMock
                .get("/v1")
                .willReturn(WireMock.okJson("[]")));

        Assertions.assertEquals(ConnectorStatus.CONNECTED, connectorService.getConnector(connector.getSecuredUuid()).getStatus());
    }

    @Test
    void testApproveConnector_ValidationFail() {
        Assertions.assertThrows(ValidationException.class, () -> connectorService.approve(connector.getSecuredUuid()));
    }

    @Test
    void testApproveConnector_notFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> connectorService.approve(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"))
        );
    }

    @Test
    void testCheckHealth() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get("/v1/health")
                .willReturn(WireMock.okJson("{ \"status\": \"ok\" }")));

        HealthDto health = connectorService.checkHealth(connector.getSecuredUuid());
        Assertions.assertNotNull(health);
        Assertions.assertNotNull(health.getStatus());
        Assertions.assertEquals(HealthStatus.OK, health.getStatus());
    }

    @Test
    void testCheckHealth_notFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> connectorService.checkHealth(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"))
        );
    }

    @Test
    void testGetAttributes() throws ConnectorException {
        FunctionGroupCode code = FunctionGroupCode.CREDENTIAL_PROVIDER;
        String kind = "ApiKey";

        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/" + code.getCode() + "/" + kind + "/attributes"))
                .willReturn(WireMock.okJson("[]")));

        List<BaseAttribute> attributes = connectorService.getAttributes(connector.getSecuredUuid(), code, kind);
        Assertions.assertNotNull(attributes);
    }

    @Test
    void testGetAttributes_validationFail() {
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
    void testGetAttributes_notFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> connectorService.getAttributes(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"),
                        null,
                        null
                )
        );
    }

    @Test
    void testValidateAttributes() throws ConnectorException {
        FunctionGroupCode code = FunctionGroupCode.CREDENTIAL_PROVIDER;
        String kind = "ApiKey";

        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/" + code.getCode() + "/" + kind + "/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        connectorService.validateAttributes(connector.getSecuredUuid(), code, List.of(), kind);

    }

    @Test
    void testValidateAttributes_validationFail() {
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
    void testValidateAttributes_validationFailOnConnector() throws ConnectorException {
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
    void testValidateAttributes_validationFailOnConnectorException() {
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

        SecuredUUID connectorUuid = connector.getSecuredUuid();
        Assertions.assertThrows(ValidationException.class,
                () -> connectorService.validateAttributes(connectorUuid, code, List.of(), kind));
    }

    @Test
    void testValidateAttributes_notFound() {
        Assertions.assertThrows(NotFoundException.class,
                () -> connectorService.validateAttributes(
                        SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"),
                        null,
                        null,
                        null
                )
        );
    }

    @Test
    void testGetObjectsForResource() {
        List<NameAndUuidDto> dtos = connectorService.listResourceObjects(SecurityFilter.create());
        Assertions.assertEquals(1, dtos.size());
    }
}
