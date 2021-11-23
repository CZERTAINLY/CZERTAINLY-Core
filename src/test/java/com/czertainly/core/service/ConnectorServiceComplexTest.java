package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.connector.*;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.Connector2FunctionGroup;
import com.czertainly.core.dao.entity.FunctionGroup;
import com.czertainly.core.dao.repository.Connector2FunctionGroupRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.FunctionGroupRepository;
import com.czertainly.core.util.MetaDefinitions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.net.ConnectException;
import java.util.Collections;
import java.util.List;

@SpringBootTest
@Transactional
@Rollback
public class ConnectorServiceComplexTest {

    @Autowired
    private ConnectorService connectorService;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private FunctionGroupRepository functionGroupRepository;
    @Autowired
    private Connector2FunctionGroupRepository connector2FunctionGroupRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private WireMockServer mockServer;

    @BeforeEach
    public void setUp() {
        mockServer = new WireMockServer(3665);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());
    }

    @AfterEach
    public void tearDown() {
        mockServer.stop();
    }

    @Test
    @WithMockUser(roles="SUPERADMINISTRATOR")
    public void testListConnectors_Empty() {
        List<ConnectorDto> connectors = connectorService.listConnectors();
        Assertions.assertNotNull(connectors);
        Assertions.assertTrue(connectors.isEmpty());
    }

    @Test
    @WithMockUser(roles="SUPERADMINISTRATOR")
    public void testListConnectors_One() {
        Connector connector = new Connector();
        connectorRepository.save(connector);

        List<ConnectorDto> connectors = connectorService.listConnectors();
        Assertions.assertNotNull(connectors);
        Assertions.assertFalse(connectors.isEmpty());
        Assertions.assertEquals(1, connectors.size());
    }

    @Test
    @WithMockUser(roles="SUPERADMINISTRATOR")
    public void testGetConnector() throws NotFoundException, ConnectorException {

        mockServer.stubFor(WireMock.get("/v1").willReturn(WireMock.okJson("[]")));

        Connector connector = new Connector();
        connector.setName("testConnector");
        connector.setUrl("http://localhost:3665");
        connector = connectorRepository.save(connector);

        ConnectorDto dto = connectorService.getConnector(connector.getUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertNotNull(dto.getUuid());
    }

    @Test
    @WithMockUser(roles="SUPERADMINISTRATOR")
    public void testCreateConnector() throws NotFoundException, AlreadyExistException {
        String kindName = "testKind";

        FunctionGroup functionGroup = new FunctionGroup();
        functionGroup.setCode(FunctionGroupCode.CREDENTIAL_PROVIDER);
        functionGroup.setName(FunctionGroupCode.CREDENTIAL_PROVIDER.getCode());
        functionGroupRepository.save(functionGroup);

        FunctionGroupDto fgDto = functionGroup.mapToDto();
        fgDto.setKinds(Collections.singletonList(kindName));

        ConnectorDto request = new ConnectorDto();
        request.setName("testConnector");
        request.setFunctionGroups(Collections.singletonList(fgDto));

        ConnectorDto dto = connectorService.createConnector(request);
        Assertions.assertNotNull(dto);
        Assertions.assertNotNull(dto.getUuid());
        Assertions.assertNotNull(dto.getFunctionGroups());
        Assertions.assertFalse(dto.getFunctionGroups().isEmpty());
        Assertions.assertEquals(1, dto.getFunctionGroups().size());

        FunctionGroupDto loaded = dto.getFunctionGroups().get(0);
        Assertions.assertEquals(FunctionGroupCode.CREDENTIAL_PROVIDER, loaded.getFunctionGroupCode());

        Assertions.assertNotNull(loaded.getKinds());
        Assertions.assertFalse(loaded.getKinds().isEmpty());
        Assertions.assertEquals(1, loaded.getKinds().size());
        Assertions.assertEquals(kindName, loaded.getKinds().get(0));

        // check database
        List<ConnectorDto> connectors = connectorService.listConnectors();
        Assertions.assertNotNull(connectors);
        Assertions.assertFalse(connectors.isEmpty());
        Assertions.assertEquals(1, connectors.size());
    }

    @Test
    @WithMockUser(roles="SUPERADMINISTRATOR")
    public void testUpdateConnector() throws NotFoundException {
        String kindName = "testKind";

        FunctionGroup caFunctionGroup = new FunctionGroup();
        caFunctionGroup.setCode(FunctionGroupCode.CA_CONNECTOR);
        caFunctionGroup.setName(FunctionGroupCode.CA_CONNECTOR.getCode());
        functionGroupRepository.save(caFunctionGroup);

        FunctionGroup discoveryFunctionGroup = new FunctionGroup();
        discoveryFunctionGroup.setCode(FunctionGroupCode.DISCOVERY_PROVIDER);
        discoveryFunctionGroup.setName(FunctionGroupCode.DISCOVERY_PROVIDER.getCode());
        functionGroupRepository.save(discoveryFunctionGroup);

        Connector connector = new Connector();
        connector.setName("testConnector");
        connector = connectorRepository.save(connector);

        addFunctionGroupToConnector(caFunctionGroup, Collections.singletonList(kindName), connector);
        addFunctionGroupToConnector(discoveryFunctionGroup, Collections.singletonList(kindName), connector);
//        connectorRepository.flush();
//        connector2FunctionGroupRepository.flush();

        FunctionGroupDto caFgDto = caFunctionGroup.mapToDto();
        caFgDto.setKinds(Collections.singletonList(kindName));

        ConnectorDto request = new ConnectorDto();
        request.setName("testConnector");
        request.setFunctionGroups(Collections.singletonList(caFgDto));

        ConnectorDto dto = connectorService.updateConnector(connector.getUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertNotNull(dto.getUuid());
        Assertions.assertNotNull(dto.getFunctionGroups());
        Assertions.assertFalse(dto.getFunctionGroups().isEmpty());
        Assertions.assertEquals(1, dto.getFunctionGroups().size());

        FunctionGroupDto loaded = dto.getFunctionGroups().get(0);
        Assertions.assertEquals(FunctionGroupCode.CA_CONNECTOR, loaded.getFunctionGroupCode());

        Assertions.assertNotNull(loaded.getKinds());
        Assertions.assertFalse(loaded.getKinds().isEmpty());
        Assertions.assertEquals(1, loaded.getKinds().size());
        Assertions.assertEquals(kindName, loaded.getKinds().get(0));

        // check database
        List<ConnectorDto> connectors = connectorService.listConnectors();
        Assertions.assertNotNull(connectors);
        Assertions.assertFalse(connectors.isEmpty());
        Assertions.assertEquals(1, connectors.size());
    }

    private void addFunctionGroupToConnector(FunctionGroup functionGroup, List<String> kinds, Connector connector) throws NotFoundException {
        Connector2FunctionGroup c2fg = new Connector2FunctionGroup();
        c2fg.setConnector(connector);
        c2fg.setFunctionGroup(functionGroup);
        c2fg.setKinds(MetaDefinitions.serializeArrayString(kinds));
        connector2FunctionGroupRepository.save(c2fg);

        connector.getFunctionGroups().add(c2fg);
        connectorRepository.save(connector);
    }

    @Test
    @WithMockUser(roles="SUPERADMINISTRATOR")
    public void testReconnectConnector() throws NotFoundException, JsonProcessingException, ConnectorException {
        String kindName = "testKind";

        FunctionGroup caFunctionGroup = new FunctionGroup();
        caFunctionGroup.setCode(FunctionGroupCode.CA_CONNECTOR);
        caFunctionGroup.setName(FunctionGroupCode.CA_CONNECTOR.getCode());
        functionGroupRepository.save(caFunctionGroup);

        FunctionGroup discoveryFunctionGroup = new FunctionGroup();
        discoveryFunctionGroup.setCode(FunctionGroupCode.DISCOVERY_PROVIDER);
        discoveryFunctionGroup.setName(FunctionGroupCode.DISCOVERY_PROVIDER.getCode());
        functionGroupRepository.save(discoveryFunctionGroup);

        FunctionGroup credentialProviderFunctionGroup = new FunctionGroup();
        credentialProviderFunctionGroup.setCode(FunctionGroupCode.CREDENTIAL_PROVIDER);
        credentialProviderFunctionGroup.setName(FunctionGroupCode.CREDENTIAL_PROVIDER.getCode());
        functionGroupRepository.save(credentialProviderFunctionGroup);

        Connector connector = new Connector();
        connector.setName("testConnector");
        connector.setUrl("http://localhost:3665");
        connector = connectorRepository.save(connector);

        addFunctionGroupToConnector(caFunctionGroup, Collections.singletonList(kindName), connector);
        addFunctionGroupToConnector(discoveryFunctionGroup, Collections.singletonList(kindName), connector);

        InfoResponse infoResponse = new InfoResponse();
        infoResponse.setFunctionGroupCode(FunctionGroupCode.CREDENTIAL_PROVIDER);
        infoResponse.setEndPoints(Collections.emptyList());
        String jsonBody = objectMapper.writeValueAsString(Collections.singletonList(infoResponse));
        mockServer.stubFor(WireMock.get("/v1")
                .willReturn(WireMock.okJson(jsonBody)));

        List<ConnectDto> dtos = connectorService.reconnect(connector.getUuid());
        Assertions.assertNotNull(dtos);
        Assertions.assertFalse(dtos.isEmpty());
        Assertions.assertEquals(1, dtos.size());
        Assertions.assertEquals(FunctionGroupCode.CREDENTIAL_PROVIDER, dtos.get(0).getFunctionGroup().getFunctionGroupCode());
    }
}
