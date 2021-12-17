package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.ca.CAInstanceDto;
import com.czertainly.api.model.ca.CAInstanceRequestDto;
import com.czertainly.api.model.connector.FunctionGroupCode;
import com.czertainly.core.dao.entity.CAInstanceReference;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.Connector2FunctionGroup;
import com.czertainly.core.dao.entity.FunctionGroup;
import com.czertainly.core.dao.repository.CAInstanceReferenceRepository;
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

@SpringBootTest
@Transactional
@Rollback
@WithMockUser(roles="SUPERADMINISTRATOR")
public class CAInstanceServiceTest {

    private static final String CA_INSTANCE_NAME = "testCAInstance1";

    @Autowired
    private CAInstanceService caInstanceService;

    @Autowired
    private CAInstanceReferenceRepository caInstanceRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private FunctionGroupRepository functionGroupRepository;
    @Autowired
    private Connector2FunctionGroupRepository connector2FunctionGroupRepository;

    private CAInstanceReference caInstance;
    private Connector connector;

    private WireMockServer mockServer;

    @BeforeEach
    public void setUp() {
        mockServer = new WireMockServer(3665);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        connector = new Connector();
        connector.setName("caInstanceConnector");
        connector.setUrl("http://localhost:3665");
        connector = connectorRepository.save(connector);

        FunctionGroup functionGroup = new FunctionGroup();
        functionGroup.setCode(FunctionGroupCode.CA_CONNECTOR);
        functionGroup.setName(FunctionGroupCode.CA_CONNECTOR.getCode());
        functionGroupRepository.save(functionGroup);

        Connector2FunctionGroup c2fg = new Connector2FunctionGroup();
        c2fg.setConnector(connector);
        c2fg.setFunctionGroup(functionGroup);
        c2fg.setKinds(MetaDefinitions.serializeArrayString(List.of("ApiKey")));
        connector2FunctionGroupRepository.save(c2fg);

        connector.getFunctionGroups().add(c2fg);
        connectorRepository.save(connector);

        caInstance = new CAInstanceReference();
        caInstance.setName(CA_INSTANCE_NAME);
        caInstance.setConnector(connector);
        caInstance.setCaInstanceUuid("1l");
        caInstance = caInstanceRepository.save(caInstance);
    }

    @AfterEach
    public void tearDown() {
        mockServer.stop();
    }

    @Test
    public void testListCAInstances() {
        List<CAInstanceDto> caInstances = caInstanceService.listCAInstances();
        Assertions.assertNotNull(caInstances);
        Assertions.assertFalse(caInstances.isEmpty());
        Assertions.assertEquals(1, caInstances.size());
        Assertions.assertEquals(caInstance.getUuid(), caInstances.get(0).getUuid());
    }

    @Test
    public void testGetCAInstance() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/caConnector/authorities/[^/]+"))
                .willReturn(WireMock.okJson("{}")));

        CAInstanceDto dto = caInstanceService.getCAInstance(caInstance.getUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(caInstance.getUuid(), dto.getUuid());
        Assertions.assertNotNull(dto.getConnectorUuid());
        Assertions.assertEquals(caInstance.getConnector().getUuid(), dto.getConnectorUuid());
    }

    @Test
    public void testGetCAInstance_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> caInstanceService.getCAInstance("wrong-uuid"));
    }

    @Test
    public void testAddCAInstance() throws ConnectorException, AlreadyExistException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/caConnector/[^/]+/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/caConnector/[^/]+/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/caConnector/authorities"))
                .willReturn(WireMock.okJson("{ \"id\": 2 }")));

        CAInstanceRequestDto request = new CAInstanceRequestDto();
        request.setName("testCAInstance2");
        request.setConnectorUuid(connector.getUuid());
        request.setAttributes(List.of());
        request.setAuthorityType("Ejbca");

        CAInstanceDto dto = caInstanceService.createCAInstance(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
        Assertions.assertNotNull(dto.getConnectorUuid());
        Assertions.assertEquals(caInstance.getConnector().getUuid(), dto.getConnectorUuid());
    }

    @Test
    public void testAddCAInstance_notFound() {
        CAInstanceRequestDto request = new CAInstanceRequestDto();
        // connector uui not set
        Assertions.assertThrows(NotFoundException.class, () -> caInstanceService.createCAInstance(request));
    }

    @Test
    public void testAddCAInstance_alreadyExist() {
        CAInstanceRequestDto request = new CAInstanceRequestDto();
        request.setName(CA_INSTANCE_NAME); // caInstance with same name exist

        Assertions.assertThrows(AlreadyExistException.class, () -> caInstanceService.createCAInstance(request));
    }

    @Test
    public void testEditCAInstance() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/caConnector/[^/]+/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/caConnector/[^/]+/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/caConnector/authorities/[^/]+"))
                .willReturn(WireMock.okJson("{ \"id\": 2 }")));

        CAInstanceRequestDto request = new CAInstanceRequestDto();
        request.setName(caInstance.getName());
        request.setConnectorUuid(connector.getUuid());
        request.setAttributes(List.of());
        request.setAuthorityType("Ejbca");

        CAInstanceDto dto = caInstanceService.updateCAInstance(caInstance.getUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getAuthorityType(), dto.getAuthorityType());
        Assertions.assertNotNull(dto.getConnectorUuid());
        Assertions.assertEquals(caInstance.getConnector().getUuid(), dto.getConnectorUuid());
    }

    @Test
    public void testEditCAInstance_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> caInstanceService.updateCAInstance("wrong-uuid", null));
    }

    @Test
    public void testRemoveCAInstance() throws ConnectorException {
        mockServer.stubFor(WireMock
                .delete(WireMock.urlPathMatching("/v1/caConnector/authorities/[^/]+"))
                .willReturn(WireMock.ok()));

        caInstanceService.removeCAInstance(caInstance.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> caInstanceService.getCAInstance(caInstance.getUuid()));
    }

    @Test
    public void testGetRaProfileAttributes() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/caConnector/authorities/[^/]+/raProfiles/attributes"))
                .willReturn(WireMock.ok()));

        caInstanceService.listRAProfileAttributes(caInstance.getUuid());
    }

    @Test
    public void testGetRaProfileAttributes_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> caInstanceService.listRAProfileAttributes("wrong-uuid"));
    }

    @Test
    public void testValidateRaProfileAttributes() throws ConnectorException {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/caConnector/authorities/[^/]+/raProfiles/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        caInstanceService.validateRAProfileAttributes(caInstance.getUuid(), List.of());
    }

    @Test
    public void testValidateRaProfileAttributes_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> caInstanceService.validateRAProfileAttributes("wrong-uuid", null));
    }

    @Test
    public void testRemoveCAInstance_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> caInstanceService.removeCAInstance("wrong-uuid"));
    }

    @Test
    public void testBulkRemove() throws NotFoundException, ConnectorException {
        caInstanceService.bulkRemoveCaInstance(List.of(caInstance.getUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> caInstanceService.getCAInstance(caInstance.getUuid()));
    }
}
