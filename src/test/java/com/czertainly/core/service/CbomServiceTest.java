package com.czertainly.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.CbomRepositoryException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.core.cbom.CbomDetailDto;
import com.czertainly.api.model.core.cbom.CbomDto;
import com.czertainly.api.model.core.cbom.CbomListResponseDto;
import com.czertainly.api.model.core.cbom.CbomUploadRequestDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.cbom.client.CbomRepositoryClient;
import com.czertainly.core.dao.entity.Cbom;
import com.czertainly.core.dao.repository.CbomRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

class CbomServiceTest extends BaseSpringBootTest {

    static {
        System.setProperty("testcontainers.docker.client.strategy", "org.testcontainers.dockerclient.UnixSocketClientProviderStrategy");
    }

    @Autowired
    private CbomService cbomService;

    @Autowired
    private AttributeEngine attributeEngine;

    @Autowired
    private CbomRepository cbomRepository;

    private WireMockServer mockServer;
    private WebClient webClient;
    private CbomRepositoryClient cbomRepositoryClient;


    @BeforeEach
    public void setUp() {
        cbomRepository.deleteAll();

        mockServer = new WireMockServer(0);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        webClient = WebClient.builder()
            .baseUrl("http://localhost:" + mockServer.port())
            .filter((request, next) -> next.exchange(request)
                .flatMap(response -> CbomRepositoryClient.handleHttpExceptions(response)))
            .exchangeStrategies(ExchangeStrategies.builder()
                .codecs(configurer -> {
                    configurer.defaultCodecs().maxInMemorySize(1024 * 1024);
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                    configurer.defaultCodecs().jackson2JsonDecoder(new org.springframework.http.codec.json.Jackson2JsonDecoder(mapper));
                    configurer.defaultCodecs().jackson2JsonEncoder(new org.springframework.http.codec.json.Jackson2JsonEncoder(mapper));
                })
                .build())
            .build();
        cbomRepositoryClient = new CbomRepositoryClient();
        ReflectionTestUtils.setField(cbomRepositoryClient, "client", webClient);

        ReflectionTestUtils.setField(cbomService, "cbomRepositoryClient", cbomRepositoryClient);
        ReflectionTestUtils.setField(cbomRepositoryClient, "cbomRepositoryBaseUrl", "");
    }

    @AfterEach
    public void tearDown() {
        mockServer.stop();
    }

    @Test
    void testlistCboms() throws NotFoundException {
        // Given
        Cbom cbom = new Cbom();
        cbom.setSerialNumber("testing");
        cbom.setCreatedAt(Instant.now());
        cbom = cbomRepository.save(cbom);

        // When
        SecurityFilter filter = new SecurityFilter();
        SearchRequestDto search = new SearchRequestDto();
        CbomListResponseDto response = cbomService.listCboms(filter, search);

        // Then
        assertNotNull(response);
        assertEquals(response.getItems().size(), 1);
        assertEquals("testing", response.getItems().get(0).getSerialNumber());
    }

    @Test
    void testgetCbom() throws NotFoundException {
        // Given
        Cbom cbom = new Cbom();
        cbom.setSerialNumber("testing");
        cbom.setCreatedAt(Instant.now());
        cbom = cbomRepository.save(cbom);

        // When
        CbomDto cbomDto = cbomService.getCbom(cbom.getSecuredUuid());

        // Then
        assertNotNull(cbomDto);
        assertEquals(cbom.getSerialNumber(), cbomDto.getSerialNumber());
    }

    @Test
    void testGetCbom_NotFound() {
        SecuredUUID uuid = SecuredUUID.fromString("807d4ff9-8bcf-4dd4-9239-3a8f2a177710");

        // When/Then
        assertThrows(NotFoundException.class, () -> cbomService.getCbom(uuid));
    }

    @Test
    void testGetCbomDetail_Success() throws Exception {
        // Given
        SecuredUUID uuid = SecuredUUID.fromString("807d4ff9-8bcf-4dd4-9239-3a8f2a177710");
        String serialNumber = "urn:uuid:3e671687-395b-41f5-a30f-a58921a69b79";
        Integer version = 1;

        // Create and save Cbom entity
        Cbom cbom = new Cbom();
        cbom.setUuid(uuid.getValue());
        cbom.setSerialNumber(serialNumber);
        cbom.setVersion(version);
        cbom.setCreatedAt(Instant.now());
        cbomRepository.save(cbom);

        // Mock WireMock response - NOTE: URL is /v1/bom/ not /v1/cboms/
        String responseBody = """
        {
        "$schema": "https://cyclonedx.org/schema/bom-1.6.schema.json",
        "bomFormat": "CycloneDX",
        "specVersion": "1.6",
        "serialNumber": "urn:uuid:3e671687-395b-41f5-a30f-a58921a69b79",
        "version": 1,
        "metadata": {},
        "components": []
        }
        """;

        // Fix: Change URL path from /v1/cboms/ to /v1/bom/
        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/bom/.*"))
            .withQueryParam("version", WireMock.equalTo(version.toString()))
            .willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)));

        // When
        CbomDetailDto result = cbomService.getCbomDetail(uuid);

        // Then
        assertNotNull(result);
        assertEquals(serialNumber, result.getSerialNumber());
        assertEquals(version.toString(), result.getVersion());
        assertNotNull(result.getContent());
        assertEquals("CycloneDX", result.getContent().get("bomFormat"));

        // Verify WireMock was called
        mockServer.verify(WireMock.getRequestedFor(
            WireMock.urlPathMatching("/v1/bom/.*"))
            .withQueryParam("version", WireMock.equalTo(version.toString())));
    }

    @Test
    void testGetCbomDetail_NotFoundInCbomRepository() throws Exception {
        // Given
        SecuredUUID uuid = SecuredUUID.fromString("807d4ff9-8bcf-4dd4-9239-3a8f2a177710");
        String serialNumber = "urn:uuid:3e671687-395b-41f5-a30f-a58921a69b79";
        Integer version = 1;

        // Create and save Cbom entity
        Cbom cbom = new Cbom();
        cbom.setUuid(uuid.getValue());
        cbom.setSerialNumber(serialNumber);
        cbom.setVersion(version);
        cbom.setCreatedAt(Instant.now());
        cbomRepository.save(cbom);

        // Mock WireMock to return 404 - Fix URL path
        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/bom/" + serialNumber))
            .willReturn(WireMock.aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"status\": 404, \"title\": \"Not Found\"}")));

        // When/Then
        assertThrows(NotFoundException.class, () -> cbomService.getCbomDetail(uuid));
    }

    @Test
    void testGetCbomDetail_CbomRepositoryError() throws Exception {
        // Given
        SecuredUUID uuid = SecuredUUID.fromString("807d4ff9-8bcf-4dd4-9239-3a8f2a177710");
        String serialNumber = "urn:uuid:3e671687-395b-41f5-a30f-a58921a69b79";
        Integer version = 1;

        // Create and save Cbom entity
        Cbom cbom = new Cbom();
        cbom.setUuid(uuid.getValue());
        cbom.setSerialNumber(serialNumber);
        cbom.setVersion(version);
        cbom.setCreatedAt(Instant.now());
        cbomRepository.save(cbom);

        // Mock WireMock to return 500 error - Fix URL path
        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/bom/.*"))
            .withQueryParam("version", WireMock.equalTo(version.toString()))
            .willReturn(WireMock.aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("""
                    {
                        "type": "about:blank",
                        "title": "Internal Server Error",
                        "status": 500,
                        "detail": "Server error occurred"
                    }
                    """)));

        // When/Then
        // When/Then - Use assertThrows with Throwable cast
        Exception exception = assertThrows(Exception.class, () -> cbomService.getCbomDetail(uuid));
        assertTrue(exception instanceof CbomRepositoryException, 
        "Expected CbomRepositoryException but got: " + exception.getClass().getName());
        CbomRepositoryException cbomException = (CbomRepositoryException) exception;
        assertNotNull(cbomException.getProblemDetail());
        assertEquals(500, cbomException.getProblemDetail().getStatus());
    }

    @Test
    void testCreateCbom_Success() throws Exception {
        // Given
        String serialNumber = "urn:uuid:3e671687-395b-41f5-a30f-a58921a69b79";
        Integer version = 1;

        Map<String, Object> content = new HashMap<>();
        content.put("serialNumber", serialNumber);
        content.put("version", version);

        CbomUploadRequestDto request = new CbomUploadRequestDto();
        request.setContent(content);

        // Mock WireMock to return successful response
        mockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/bom"))
            .willReturn(WireMock.aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                    "serialNumber": "urn:uuid:3e671687-395b-41f5-a30f-a58921a69b79",
                    "version": 1,
                    "cryptoStats": {
                    "cryptoAssets": {
                    "algorithms": {
                    "total": 5
                    },
                    "certificates": {
                    "total": 3
                    },
                    "protocols": {
                    "total": 2
                    },
                    "relatedCryptoMaterials": {
                    "total": 4
                    },
                    "total": 14
                    }
                    }
                    }
                    """)));

        // When
        CbomDto result = cbomService.createCbom(request);

        // Then
        assertNotNull(result);
        assertEquals(serialNumber, result.getSerialNumber());
        assertEquals(version.toString(), result.getVersion());
        assertEquals(5, result.getAlgorithms());
        assertEquals(3, result.getCertificates());
        assertEquals(2, result.getProtocols());
        assertEquals(4, result.getCryptoMaterial());
        assertEquals(14, result.getTotalAssets());

        // Verify entity was saved to database
        List<Cbom> savedCboms = cbomRepository.findAll();
        assertEquals(1, savedCboms.size());
        assertEquals(serialNumber, savedCboms.get(0).getSerialNumber());
        assertEquals("CORE", savedCboms.get(0).getSource());

        // Assert
        mockServer.verify(WireMock.postRequestedFor(
            WireMock.urlEqualTo("/v1/bom")));
    }

    @Test
    void testGetCbomVersions() throws NotFoundException {
        // Given
        String serialNumber = "urn:uuid:test-123";

        Cbom cbom1 = new Cbom();
        cbom1.setSerialNumber(serialNumber);
        cbom1.setVersion(1);
        cbom1.setCreatedAt(Instant.now());
        cbom1 = cbomRepository.save(cbom1);

        Cbom cbom2 = new Cbom();
        cbom2.setSerialNumber(serialNumber);
        cbom2.setVersion(2);
        cbom2.setCreatedAt(Instant.now().plusSeconds(60));
        cbom2 = cbomRepository.save(cbom2);

        Cbom cbom3 = new Cbom();
        cbom3.setSerialNumber(serialNumber);
        cbom3.setVersion(3);
        cbom3.setCreatedAt(Instant.now().plusSeconds(120));
        cbom3 = cbomRepository.save(cbom3);

        // When
        List<CbomDto> versions = cbomService.getCbomVersions(serialNumber);

        // Then
        assertNotNull(versions);
        assertEquals(3, versions.size());
        assertEquals(serialNumber, versions.get(0).getSerialNumber());
        assertEquals(serialNumber, versions.get(1).getSerialNumber());
        assertEquals(serialNumber, versions.get(2).getSerialNumber());
    }

    @Test
    void testGetCbomVersions_EmptyList() throws NotFoundException {
        // Given
        String serialNumber = "urn:uuid:non-existent";

        // When
        List<CbomDto> versions = cbomService.getCbomVersions(serialNumber);

        // Then
        assertNotNull(versions);
        assertEquals(0, versions.size());
    }

    @Test
    void testGetCbomVersions_SingleVersion() throws NotFoundException {
        // Given
        String serialNumber = "urn:uuid:single-version";

        Cbom cbom = new Cbom();
        cbom.setSerialNumber(serialNumber);
        cbom.setVersion(1);
        cbom.setCreatedAt(Instant.now());
        cbom = cbomRepository.save(cbom);

        // When
        List<CbomDto> versions = cbomService.getCbomVersions(serialNumber);

        // Then
        assertNotNull(versions);
        assertEquals(1, versions.size());
        assertEquals(serialNumber, versions.get(0).getSerialNumber());
        assertEquals("1", versions.get(0).getVersion());
    }

    @Test
    void testGetCbomVersions_MultipleSerialNumbers() throws NotFoundException {
        // Given
        String serialNumber1 = "urn:uuid:test-456";
        String serialNumber2 = "urn:uuid:test-789";

        Cbom cbom1 = new Cbom();
        cbom1.setSerialNumber(serialNumber1);
        cbom1.setVersion(1);
        cbom1.setCreatedAt(Instant.now());
        cbomRepository.save(cbom1);

        Cbom cbom2 = new Cbom();
        cbom2.setSerialNumber(serialNumber2);
        cbom2.setVersion(1);
        cbom2.setCreatedAt(Instant.now());
        cbomRepository.save(cbom2);

        // When
        List<CbomDto> versions = cbomService.getCbomVersions(serialNumber1);

        // Then
        assertNotNull(versions);
        assertEquals(1, versions.size());
        assertEquals(serialNumber1, versions.get(0).getSerialNumber());
    }

    @Test
    void testUploadCbom_MissingContent() throws CbomRepositoryException {
        // Given - null content
        CbomUploadRequestDto request = new CbomUploadRequestDto();

        // When / Then
        assertThrows(ValidationException.class, () -> 
            cbomService.createCbom(request)
        );
    }

    @Test
    void testUploadCbom_MissingSerialNumber() throws CbomRepositoryException {
        // Given
        Map<String, Object> content = new HashMap<>();
        // Missing serialNumber
        content.put("bomFormat", "CycloneDX");
        content.put("specVersion", "1.5");
        content.put("version", 1);

        CbomUploadRequestDto request = new CbomUploadRequestDto();
        request.setContent(content);

        // When / Then
        assertThrows(ValidationException.class, () -> 
            cbomService.createCbom(request)
        );
    }

    @Test
    void testUploadCbom_MissingVersion() throws CbomRepositoryException {
        // Given
        Map<String, Object> content = new HashMap<>();
        content.put("serialNumber", "urn:uuid:test-123");
        content.put("bomFormat", "CycloneDX");
        content.put("specVersion", "1.5");
        // Missing version

        CbomUploadRequestDto request = new CbomUploadRequestDto();
        request.setContent(content);

        // When / Then
        assertThrows(ValidationException.class, () -> 
            cbomService.createCbom(request)
        );
    }


    @Test
    void testUploadCbom_VersionNAN() throws CbomRepositoryException {
        // Given
        Map<String, Object> content = new HashMap<>();
        content.put("serialNumber", "urn:uuid:test-123");
        content.put("bomFormat", "CycloneDX");
        content.put("specVersion", "1.5");
        content.put("version", "1.5");

        CbomUploadRequestDto request = new CbomUploadRequestDto();
        request.setContent(content);

        // When / Then
        assertThrows(ValidationException.class, () -> 
            cbomService.createCbom(request)
        );
    }

    @Test
    void testCreateCbom_AlreadyExists_409Response() throws Exception {
        // Given
        String serialNumber = "urn:uuid:test-123";
        Integer version = 1;

        Map<String, Object> content = new HashMap<>();
        content.put("serialNumber", serialNumber);
        content.put("bomFormat", "CycloneDX");
        content.put("specVersion", "1.5");
        content.put("version", version);

        CbomUploadRequestDto request = new CbomUploadRequestDto();
        request.setContent(content);

        // Mock WireMock to return 409 Conflict
        mockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/bom"))
            .willReturn(WireMock.aResponse()
                .withStatus(409)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("""
                    {
                    "type": "about:blank",
                    "title": "Conflict",
                    "status": 409,
                    "detail": "CBOM with this serial number and version already exists"
                    }
                    """)));

        // When / Then
        AlreadyExistException exception = assertThrows(AlreadyExistException.class, () ->
            cbomService.createCbom(request)
        );

        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("CBOM with given serial number and version already exists"));

        // Verify entity was NOT saved to database
        List<Cbom> savedCboms = cbomRepository.findAll();
        assertEquals(0, savedCboms.size());

        mockServer.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/v1/bom")));
    }

    @Test
    void testCreateCbom_RepositoryServerError_500() throws Exception {
        // Given
        String serialNumber = "urn:uuid:server-error";
        Integer version = 1;

        Map<String, Object> content = new HashMap<>();
        content.put("serialNumber", serialNumber);
        content.put("bomFormat", "CycloneDX");
        content.put("specVersion", "1.5");
        content.put("version", version);

        CbomUploadRequestDto request = new CbomUploadRequestDto();
        request.setContent(content);

        // Mock WireMock to return 500 Server Error
        mockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/bom"))
            .willReturn(WireMock.aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("""
                    {
                    "type": "about:blank",
                    "title": "Internal Server Error",
                    "status": 500,
                    "detail": "Server error occurred"
                    }
                    """)));

        // When / Then
        assertThrows(CbomRepositoryException.class, () ->
            cbomService.createCbom(request)
        );

        // Verify entity was NOT saved to database
        List<Cbom> savedCboms = cbomRepository.findAll();
        assertEquals(0, savedCboms.size());

        mockServer.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/v1/bom")));
    }

    @Test
    void testCreateCbom_BadRequest_400() throws Exception {
        // Given
        String serialNumber = "urn:uuid:bad-request";
        Integer version = 1;

        Map<String, Object> content = new HashMap<>();
        content.put("serialNumber", serialNumber);
        content.put("specVersion", "1.5");
        content.put("version", version);

        CbomUploadRequestDto request = new CbomUploadRequestDto();
        request.setContent(content);

        // Mock WireMock to return 400 Bad Request
        mockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/bom"))
            .willReturn(WireMock.aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("""
                    {
                    "type": "about:blank",
                    "title": "Bad Request",
                    "status": 400,
                    "detail": "Unsupported spec version 1.5"
                    }
                    """)));

        // When / Then
        assertThrows(CbomRepositoryException.class, () ->
            cbomService.createCbom(request)
        );

        mockServer.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/v1/bom")));
    }

    @Test
    void testCreateCbom_MultipleCreations() throws Exception {
        // Given
        CbomUploadRequestDto request1 = new CbomUploadRequestDto();
        request1.setContent(Map.of("serialNumber", "urn:uuid:first", "version", 1));

        CbomUploadRequestDto request2 = new CbomUploadRequestDto();
        request2.setContent(Map.of("serialNumber", "urn:uuid:second", "version", 1));

        mockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/bom"))
            .willReturn(WireMock.aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                    "serialNumber": "urn:uuid:placeholder",
                    "version": 1,
                    "cryptoStats": {
                    "cryptoAssets": {
                    "algorithms": {"total": 1},
                    "certificates": {"total": 1},
                    "protocols": {"total": 1},
                    "relatedCryptoMaterials": {"total": 1},
                    "total": 4
                    }
                    }
                    }
                    """)));

        // When
        CbomDto result1 = cbomService.createCbom(request1);
        CbomDto result2 = cbomService.createCbom(request2);

        // Then
        assertNotNull(result1);
        assertNotNull(result2);

        List<Cbom> savedCboms = cbomRepository.findAll();
        assertEquals(2, savedCboms.size());

        mockServer.verify(2, WireMock.postRequestedFor(WireMock.urlEqualTo("/v1/bom")));
    }
}
