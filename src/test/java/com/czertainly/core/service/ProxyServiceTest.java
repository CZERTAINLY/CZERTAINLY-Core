package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.proxy.ProxyRequestDto;
import com.czertainly.api.model.client.proxy.ProxyUpdateRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.proxy.ProxyDto;
import com.czertainly.api.model.core.proxy.ProxyStatus;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.Proxy;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.ProxyRepository;
import com.czertainly.core.provisioning.ProxyProvisioningException;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

class ProxyServiceTest extends BaseSpringBootTest {

    private static final String PROXY_NAME = "testProxy1";
    private static final String INSTALLATION_INSTRUCTIONS_JSON = """
        {
            "command": {
                "shell": "helm install test-proxy oci://registry.example.com/charts/proxy --version 1.0.0 --namespace default --wait --atomic --set config.key=value"
            }
        }
        """;

    @RegisterExtension
    static WireMockExtension wireMockServer = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    @DynamicPropertySource
    static void proxyProvisioningTestProperties(@NonNull DynamicPropertyRegistry registry) {
        registry.add("proxy.provisioning.api.url", () -> wireMockServer.baseUrl());
    }

    @Autowired
    private ProxyService proxyService;

    @Autowired
    private ProxyRepository proxyRepository;

    @Autowired
    private ConnectorRepository connectorRepository;

    private Proxy proxy;

    @BeforeEach
    public void setUp() {
        proxy = new Proxy();
        proxy.setName(PROXY_NAME);
        proxy.setDescription("Test Proxy 1");
        proxy.setCode("TEST_PROXY_1");
        proxy.setStatus(ProxyStatus.CONNECTED);
        proxy = proxyRepository.save(proxy);
    }

    @Test
    void testListProxies() throws NotFoundException {
        List<ProxyDto> proxies = proxyService.listProxies(SecurityFilter.create(), Optional.empty());
        Assertions.assertNotNull(proxies);
        Assertions.assertFalse(proxies.isEmpty());
        Assertions.assertEquals(1, proxies.size());
        Assertions.assertEquals(proxy.getUuid().toString(), proxies.getFirst().getUuid());
    }

    @Test
    void testListProxiesByStatus() throws NotFoundException {
        List<ProxyDto> proxies = proxyService.listProxies(
            SecurityFilter.create(),
            Optional.of(ProxyStatus.CONNECTED)
        );
        Assertions.assertNotNull(proxies);
        Assertions.assertFalse(proxies.isEmpty());
        Assertions.assertEquals(1, proxies.size());
        Assertions.assertEquals(proxy.getUuid().toString(), proxies.getFirst().getUuid());
    }

    @Test
    void testListProxiesByStatus_notFound() throws NotFoundException {
        List<ProxyDto> proxies = proxyService.listProxies(
            SecurityFilter.create(),
            Optional.of(ProxyStatus.FAILED)
        );
        Assertions.assertNotNull(proxies);
        Assertions.assertTrue(proxies.isEmpty());
    }

    @Test
    void testGetProxy() throws NotFoundException {
        ProxyDto dto = proxyService.getProxy(proxy.getSecuredUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(proxy.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals(proxy.getName(), dto.getName());
    }

    @Test
    void testGetProxy_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> proxyService.getProxy(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testAddProxy() throws AlreadyExistException {
        // Stub for POST /api/v1/proxies
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/proxies"))
            .willReturn(aResponse()
                .withStatus(201)));

        // Stub for GET /api/v1/proxies/{code}/installation?format=helm
        wireMockServer.stubFor(get(urlPathMatching("/api/v1/proxies/.*/installation"))
            .withQueryParam("format", equalTo("helm"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(INSTALLATION_INSTRUCTIONS_JSON)));

        ProxyRequestDto request = new ProxyRequestDto();
        request.setName("testProxy2");
        request.setDescription("Test Proxy 2");

        ProxyDto dto = proxyService.createProxy(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
        Assertions.assertNotNull(dto.getInstallationInstructions());
        Assertions.assertTrue(dto.getInstallationInstructions().contains("helm install"));
    }

    @Test
    void testAddProxy_validationFail() {
        ProxyRequestDto request = new ProxyRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> proxyService.createProxy(request));
    }

    @Test
    void testAddProxy_alreadyExist() {
        ProxyRequestDto request = new ProxyRequestDto();
        request.setName(PROXY_NAME);
        Assertions.assertThrows(AlreadyExistException.class, () -> proxyService.createProxy(request));
    }

    @Test
    void testEditProxy() throws NotFoundException {
        ProxyUpdateRequestDto request = new ProxyUpdateRequestDto();
        request.setDescription("Updated Test Proxy 1");

        ProxyDto dto = proxyService.editProxy(proxy.getSecuredUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(proxy.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals(proxy.getName(), dto.getName());
        Assertions.assertEquals("Updated Test Proxy 1", dto.getDescription());
    }

    @Test
    void testEditProxy_notFound() {
        ProxyUpdateRequestDto request = new ProxyUpdateRequestDto();
        request.setDescription("Updated Description");
        Assertions.assertThrows(NotFoundException.class, () -> proxyService.editProxy(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), request));
    }

    @Test
    void testDeleteProxy() throws NotFoundException {
        wireMockServer.stubFor(delete(urlPathEqualTo("/api/v1/proxies/TEST_PROXY_DELETE"))
            .willReturn(aResponse()
                .withStatus(204)));

        Proxy proxyToDelete = new Proxy();
        proxyToDelete.setName("testProxyDelete");
        proxyToDelete.setDescription("Test Proxy to Delete");
        proxyToDelete.setCode("TEST_PROXY_DELETE");
        proxyToDelete.setStatus(ProxyStatus.CONNECTED);
        proxyToDelete = proxyRepository.save(proxyToDelete);

        proxyService.deleteProxy(proxyToDelete.getSecuredUuid());

        Assertions.assertTrue(proxyRepository.findByUuid(proxyToDelete.getUuid()).isEmpty());
    }

    @Test
    void testDeleteProxy_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> proxyService.deleteProxy(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testDeleteProxy_withConnector() {
        Proxy proxyWithConnector = new Proxy();
        proxyWithConnector.setName("testProxyWithConnector");
        proxyWithConnector.setDescription("Test Proxy with Connector");
        proxyWithConnector.setCode("TEST_PROXY_WITH_CONNECTOR");
        proxyWithConnector.setStatus(ProxyStatus.CONNECTED);
        proxyWithConnector = proxyRepository.save(proxyWithConnector);

        Connector connector = new Connector();
        connector.setName("testConnector");
        connector.setUrl("http://localhost:8080");
        connector.setProxy(proxyWithConnector);
        connectorRepository.save(connector);

        Proxy finalProxyWithConnector = proxyRepository.save(proxyWithConnector);
        Assertions.assertThrows(ValidationException.class, () -> proxyService.deleteProxy(finalProxyWithConnector.getSecuredUuid()));
    }

    @Test
    void testGetObjectsForResource() {
        List<NameAndUuidDto> dtos = proxyService.listResourceObjects(SecurityFilter.create());
        Assertions.assertEquals(1, dtos.size());
    }

    @Test
    void testGetProxy_waitingForInstallation() throws NotFoundException {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/proxies/WAITING_PROXY/installation"))
            .withQueryParam("format", equalTo("helm"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(INSTALLATION_INSTRUCTIONS_JSON)));

        Proxy waitingProxy = new Proxy();
        waitingProxy.setName("waitingProxy");
        waitingProxy.setDescription("Waiting Proxy");
        waitingProxy.setCode("WAITING_PROXY");
        waitingProxy.setStatus(ProxyStatus.WAITING_FOR_INSTALLATION);
        waitingProxy = proxyRepository.save(waitingProxy);

        ProxyDto dto = proxyService.getProxy(waitingProxy.getSecuredUuid());

        Assertions.assertNotNull(dto);
        Assertions.assertNotNull(dto.getInstallationInstructions());
        Assertions.assertTrue(dto.getInstallationInstructions().contains("helm install"));
    }

    @Test
    void testGetInstallationInstructions() throws NotFoundException {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/proxies/TEST_PROXY_1/installation"))
            .withQueryParam("format", equalTo("helm"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(INSTALLATION_INSTRUCTIONS_JSON)));

        ProxyDto dto = proxyService.getInstallationInstructions(proxy.getSecuredUuid());

        Assertions.assertNotNull(dto);
        Assertions.assertNotNull(dto.getInstallationInstructions());
        Assertions.assertTrue(dto.getInstallationInstructions().contains("helm install"));
        Assertions.assertTrue(dto.getInstallationInstructions().contains("oci://registry.example.com/charts/proxy"));
    }

    @Test
    void testCreateProxy_provisioningApiFails() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/proxies"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));

        ProxyRequestDto request = new ProxyRequestDto();
        request.setName("failingProxy");
        request.setDescription("Proxy that fails to provision");

        Assertions.assertThrows(ProxyProvisioningException.class, () -> proxyService.createProxy(request));

        // Verify proxy was not persisted due to transaction rollback
        Assertions.assertTrue(proxyRepository.findByName("failingProxy").isEmpty());
    }

    @Test
    void testCreateProxy_getInstallationInstructionsFails() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/proxies"))
            .willReturn(aResponse()
                .withStatus(201)));

        wireMockServer.stubFor(get(urlPathMatching("/api/v1/proxies/.*/installation"))
            .withQueryParam("format", equalTo("helm"))
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Service Unavailable")));

        ProxyRequestDto request = new ProxyRequestDto();
        request.setName("failingProxy2");
        request.setDescription("Proxy that fails to get installation instructions");

        Assertions.assertThrows(ProxyProvisioningException.class, () -> proxyService.createProxy(request));

        // Verify proxy was not persisted due to transaction rollback
        Assertions.assertTrue(proxyRepository.findByName("failingProxy2").isEmpty());
    }

    @Test
    void testDeleteProxy_decommissioningFails_rollbacksTransaction() {
        wireMockServer.stubFor(delete(urlPathEqualTo("/api/v1/proxies/DECOMMISSION_FAIL"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));

        Proxy proxyToDelete = new Proxy();
        proxyToDelete.setName("decommissionFailProxy");
        proxyToDelete.setDescription("Proxy that fails to decommission");
        proxyToDelete.setCode("DECOMMISSION_FAIL");
        proxyToDelete.setStatus(ProxyStatus.CONNECTED);
        proxyToDelete = proxyRepository.save(proxyToDelete);

        SecuredUUID proxyUuid = proxyToDelete.getSecuredUuid();
        Assertions.assertThrows(ProxyProvisioningException.class, () -> proxyService.deleteProxy(proxyUuid));

        // Verify proxy still exists due to transaction rollback
        Assertions.assertTrue(proxyRepository.findByUuid(proxyToDelete.getUuid()).isPresent());
    }

    @Test
    void testGetProxy_waitingForInstallation_apiFails() throws NotFoundException {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/proxies/WAITING_FAIL/installation"))
            .withQueryParam("format", equalTo("helm"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));

        Proxy waitingProxy = new Proxy();
        waitingProxy.setName("waitingFailProxy");
        waitingProxy.setDescription("Waiting Proxy that fails");
        waitingProxy.setCode("WAITING_FAIL");
        waitingProxy.setStatus(ProxyStatus.WAITING_FOR_INSTALLATION);
        waitingProxy = proxyRepository.save(waitingProxy);

        SecuredUUID proxyUuid = waitingProxy.getSecuredUuid();
        ProxyDto dto = proxyService.getProxy(proxyUuid);
        Assertions.assertNotNull(dto);
        Assertions.assertNull(dto.getInstallationInstructions());
    }

    @Test
    void testGetInstallationInstructions_apiFails() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/proxies/TEST_PROXY_1/installation"))
            .withQueryParam("format", equalTo("helm"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));

        Assertions.assertThrows(ProxyProvisioningException.class,
            () -> proxyService.getInstallationInstructions(proxy.getSecuredUuid()));
    }
}
