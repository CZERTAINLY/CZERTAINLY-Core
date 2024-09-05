package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.certificate.DiscoveryResponseDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.discovery.DiscoveryDto;
import com.czertainly.api.model.client.discovery.DiscoveryHistoryDetailDto;
import com.czertainly.api.model.client.discovery.DiscoveryHistoryDto;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.discovery.DiscoveryStatus;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.Connector2FunctionGroup;
import com.czertainly.core.dao.entity.DiscoveryHistory;
import com.czertainly.core.dao.entity.FunctionGroup;
import com.czertainly.core.dao.repository.Connector2FunctionGroupRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.DiscoveryRepository;
import com.czertainly.core.dao.repository.FunctionGroupRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.MetaDefinitions;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class DiscoveryServiceTest extends BaseSpringBootTest {

    private static final String DISCOVERY_NAME = "testDiscovery1";

    @Autowired
    private DiscoveryService discoveryService;

    @Autowired
    private DiscoveryRepository discoveryRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private FunctionGroupRepository functionGroupRepository;
    @Autowired
    private Connector2FunctionGroupRepository connector2FunctionGroupRepository;

    private DiscoveryHistory discovery;
    private Connector connector;

    private WireMockServer mockServer;

    @BeforeEach
    public void setUp() {
        mockServer = new WireMockServer(0);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        connector = new Connector();
        connector.setName("discoveryProviderConnector");
        connector.setUrl("http://localhost:"+mockServer.port());
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        FunctionGroup functionGroup = new FunctionGroup();
        functionGroup.setCode(FunctionGroupCode.DISCOVERY_PROVIDER);
        functionGroup.setName(FunctionGroupCode.DISCOVERY_PROVIDER.getCode());
        functionGroupRepository.save(functionGroup);

        Connector2FunctionGroup c2fg = new Connector2FunctionGroup();
        c2fg.setConnector(connector);
        c2fg.setFunctionGroup(functionGroup);
        c2fg.setKinds(MetaDefinitions.serializeArrayString(List.of("IpAndPort")));
        connector2FunctionGroupRepository.save(c2fg);

        connector.getFunctionGroups().add(c2fg);
        connectorRepository.save(connector);

        discovery = new DiscoveryHistory();
        discovery.setName(DISCOVERY_NAME);
        discovery.setConnectorUuid(connector.getUuid());
        discovery.setConnectorName(connector.getName());
        discovery.setStatus(DiscoveryStatus.IN_PROGRESS);
        discovery.setConnectorStatus(DiscoveryStatus.IN_PROGRESS);
        discovery = discoveryRepository.save(discovery);
    }

    @AfterEach
    public void tearDown() {
        mockServer.stop();
    }

    @Test
    public void testListDiscoveries() {
        final DiscoveryResponseDto discoveryHistoryDTO = discoveryService.listDiscoveries(SecurityFilter.create(), new SearchRequestDto());
        final List<DiscoveryHistoryDto> discoveries = discoveryHistoryDTO.getDiscoveries();
        Assertions.assertNotNull(discoveries);
        Assertions.assertFalse(discoveries.isEmpty());
        Assertions.assertEquals(1, discoveries.size());
        Assertions.assertEquals(discovery.getUuid().toString(), discoveries.get(0).getUuid());
    }

    @Test
    public void testGetDiscovery() throws NotFoundException {
        DiscoveryHistoryDetailDto dto = discoveryService.getDiscovery(discovery.getSecuredUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(discovery.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals(discovery.getConnectorUuid().toString(), dto.getConnectorUuid());
    }

    @Test
    public void testGetDiscovery_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> discoveryService.getDiscovery(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testAddDiscovery() throws ConnectorException, AlreadyExistException, AttributeException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/discoveryProvider/[^/]+/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/discoveryProvider/[^/]+/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        DiscoveryDto request = new DiscoveryDto();
        request.setName("testDiscovery2");
        request.setConnectorUuid(connector.getUuid().toString());
        request.setAttributes(List.of());
        request.setKind("ApiKey");

        DiscoveryHistoryDetailDto dto = discoveryService.createDiscovery(request, true);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
        Assertions.assertEquals(discovery.getConnectorUuid().toString(), dto.getConnectorUuid());
    }

    @Test
    public void testAddDiscovery_notFound() {
        DiscoveryDto request = new DiscoveryDto();
        request.setName("Demo");
        // connector uui not set
        Assertions.assertThrows(ValidationException.class, () -> discoveryService.createDiscovery(request, true));
    }

    @Test
    public void testAddDiscovery_alreadyExist() {
        DiscoveryDto request = new DiscoveryDto();
        request.setName(DISCOVERY_NAME); // discovery with same name exist

        Assertions.assertThrows(AlreadyExistException.class, () -> discoveryService.createDiscovery(request, true));
    }

    @Test
    @Disabled("Currently there is not valid input parameters")
    public void testDiscoverCertificates() throws ConnectorException, AlreadyExistException {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/discoveryProvider/[^/]+/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        // TODO createDiscovery is async - currently not tested properly
        discoveryService.runDiscovery(discovery.getUuid());
    }

    @Test
    @Disabled("Async method is not throwing exception")
    public void testDiscoverCertificates_notFound() {
        // connector uui not set
        Assertions.assertThrows(NotFoundException.class, () -> discoveryService.runDiscovery(discovery.getUuid()));
    }

    @Test
    @Disabled("Async method is not throwing exception")
    public void testDiscoverCertificates_validationFailed() throws ConnectorException, AlreadyExistException {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/discoveryProvider/[^/]+/attributes/validate"))
                .willReturn(WireMock.okJson("false")));

        Assertions.assertThrows(ValidationException.class, () -> discoveryService.runDiscovery(discovery.getUuid()));
    }

    @Test
    public void testRemoveDiscovery() throws NotFoundException {
        discoveryService.deleteDiscovery(discovery.getSecuredUuid());
        Assertions.assertThrows(NotFoundException.class, () -> discoveryService.getDiscovery(discovery.getSecuredUuid()));
    }

    @Test
    public void testRemoveDiscovery_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> discoveryService.deleteDiscovery(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testBulkRemove() throws NotFoundException {
        discoveryService.bulkRemoveDiscovery(List.of(discovery.getSecuredUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> discoveryService.getDiscovery(discovery.getSecuredUuid()));
    }
}
