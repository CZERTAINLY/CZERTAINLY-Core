package com.czertainly.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.CbomRepositoryException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cbom.CbomDetailDto;
import com.czertainly.api.model.core.cbom.CbomDto;
import com.czertainly.api.model.core.cbom.CbomUploadRequestDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.cbom.client.CbomRepositoryClient;
import com.czertainly.core.dao.entity.Cbom;
import com.czertainly.core.dao.repository.CbomRepository;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

class CbomServiceTest extends BaseSpringBootTest {
    @Autowired
    private CbomService cbomService;

    @Autowired
    private CbomRepository cbomRepository;

    @MockitoBean
    private AttributeEngine attributeEngine;

    private WireMockServer mockServer;
    private WebClient webClient;
    private CbomRepositoryClient cbomRepositoryClient;


    @BeforeEach
    void setUp() {
        cbomRepository.deleteAll();

        mockServer = new WireMockServer(0);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        webClient = WebClient.builder()
            .baseUrl("http://localhost:" + mockServer.port())
            .filter((request, next) -> next.exchange(request)
                .flatMap(CbomRepositoryClient::handleHttpExceptions))
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
    void tearDown() {
        mockServer.stop();
    }

    @Test
    void testListCboms() {
        // Given
        Cbom cbom = new Cbom();
        cbom.setSerialNumber("testing");
        cbom.setTimestamp(OffsetDateTime.now());
        cbom.setVersion(1);
        cbom.setSpecVersion("1.6");
        cbomRepository.save(cbom);

        // When
        SecurityFilter filter = new SecurityFilter();
        SearchRequestDto search = new SearchRequestDto();
        PaginationResponseDto<CbomDto> response = cbomService.listCboms(filter, search);

        // Then
        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals("testing", response.getItems().get(0).getSerialNumber());
    }

    @Test
    void testGetCbomDetail_Success() throws Exception {
        // Given
        SecuredUUID uuid = SecuredUUID.fromString("807d4ff9-8bcf-4dd4-9239-3a8f2a177710");
        String serialNumber = "urn:uuid:3e671687-395b-41f5-a30f-a58921a69b79";
        int version = 1;

        // Create and save Cbom entity
        Cbom cbom = new Cbom();
        cbom.setUuid(uuid.getValue());
        cbom.setSerialNumber(serialNumber);
        cbom.setVersion(version);
        cbom.setSpecVersion("1.6");
        cbom.setTimestamp(OffsetDateTime.now());
        cbomRepository.save(cbom);

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

        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/bom/.*"))
            .withQueryParam("version", WireMock.equalTo(Integer.toString(version)))
            .willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)));

        // When
        CbomDetailDto result = cbomService.getCbomDetail(uuid);

        // Then
        assertNotNull(result);
        assertEquals(serialNumber, result.getSerialNumber());
        assertEquals(version, result.getVersion());
        assertNotNull(result.getContent());
        assertEquals("CycloneDX", result.getContent().get("bomFormat"));

        // Verify WireMock was called
        mockServer.verify(WireMock.getRequestedFor(
            WireMock.urlPathMatching("/v1/bom/.*"))
            .withQueryParam("version", WireMock.equalTo(Integer.toString(version))));
    }

    @Test
    void testGetCbomDetail_NotFoundInCbomRepository() {
        // Given
        SecuredUUID uuid = SecuredUUID.fromString("807d4ff9-8bcf-4dd4-9239-3a8f2a177710");
        String serialNumber = "urn:uuid:3e671687-395b-41f5-a30f-a58921a69b79";
        int version = 1;

        // Create and save Cbom entity
        Cbom cbom = new Cbom();
        cbom.setUuid(uuid.getValue());
        cbom.setSerialNumber(serialNumber);
        cbom.setVersion(version);
        cbom.setSpecVersion("1.6");
        cbom.setTimestamp(OffsetDateTime.now());
        cbomRepository.save(cbom);

        // Mock WireMock to return 404 for the requested BOM
        mockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/bom/" + serialNumber))
            .willReturn(WireMock.aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"status\": 404, \"title\": \"Not Found\"}")));

        // When/Then
        assertThrows(NotFoundException.class, () -> cbomService.getCbomDetail(uuid));
    }

    @Test
    void testGetCbomDetail_CbomRepositoryError() {
        // Given
        SecuredUUID uuid = SecuredUUID.fromString("807d4ff9-8bcf-4dd4-9239-3a8f2a177710");
        String serialNumber = "urn:uuid:3e671687-395b-41f5-a30f-a58921a69b79";
        int version = 1;

        // Create and save Cbom entity
        Cbom cbom = new Cbom();
        cbom.setUuid(uuid.getValue());
        cbom.setSerialNumber(serialNumber);
        cbom.setVersion(version);
        cbom.setSpecVersion("1.6");
        cbom.setTimestamp(OffsetDateTime.now());
        cbomRepository.save(cbom);

        // Mock WireMock to return 500 error for the CBOM repository endpoint
        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/bom/.*"))
            .withQueryParam("version", WireMock.equalTo(Integer.toString(version)))
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
        int version = 1;
        OffsetDateTime timestamp = OffsetDateTime.now();

        Map<String, Object> content = new HashMap<>();
        content.put("serialNumber", serialNumber);
        content.put("version", version);
        content.put("specVersion", "1.6");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", timestamp.toString());
        Map<String, Object> component = new HashMap<>();
        component.put("name", "CORE");
        metadata.put("component", component);
        content.put("metadata", metadata);

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
        assertEquals(version, result.getVersion());
        assertEquals("1.6", result.getSpecVersion());
        assertEquals(5, result.getAlgorithms());
        assertEquals(3, result.getCertificates());
        assertEquals(2, result.getProtocols());
        assertEquals(4, result.getCryptoMaterial());
        assertEquals(14, result.getTotalAssets());

        // Verify entity was saved to database
        List<Cbom> savedCboms = cbomRepository.findAll();
        assertEquals(1, savedCboms.size());
        assertEquals(serialNumber, savedCboms.getFirst().getSerialNumber());
        assertEquals("CORE", savedCboms.getFirst().getSource());

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
        cbom1.setSpecVersion("1.6");
        cbom1.setTimestamp(OffsetDateTime.now());
        cbom1 = cbomRepository.save(cbom1);

        Cbom cbom2 = new Cbom();
        cbom2.setSerialNumber(serialNumber);
        cbom2.setVersion(2);
        cbom2.setTimestamp(OffsetDateTime.now());
        cbom2.setSpecVersion("1.6");
        cbom2 = cbomRepository.save(cbom2);

        Cbom cbom3 = new Cbom();
        cbom3.setSerialNumber(serialNumber);
        cbom3.setVersion(3);
        cbom3.setTimestamp(OffsetDateTime.now());
        cbom3.setSpecVersion("1.6");
        cbom3 = cbomRepository.save(cbom3);

        List<UUID> uuids = List.of(cbom1, cbom2, cbom3)
            .stream()
            .map(Cbom::getUuid)
            .toList();

        for (UUID uuid: uuids) {
            // When
            List<CbomDto> versions = cbomService.getCbomVersions(SecuredUUID.fromUUID(uuid));

            // Then
            assertNotNull(versions);
            assertEquals(3, versions.size());
            assertEquals(serialNumber, versions.get(0).getSerialNumber());
            assertEquals(serialNumber, versions.get(1).getSerialNumber());
            assertEquals(serialNumber, versions.get(2).getSerialNumber());
        }
    }

    @Test
    void testGetCbomVersions_EmptyList() throws NotFoundException {
        // Given
        SecuredUUID uuid = SecuredUUID.fromString("100a1949-cdf9-49bb-a580-d34aa2f39225");

        // When
        List<CbomDto> versions = cbomService.getCbomVersions(uuid);

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
        cbom.setSpecVersion("1.6");
        cbom.setTimestamp(OffsetDateTime.now());
        cbom = cbomRepository.save(cbom);

        // When
        List<CbomDto> versions = cbomService.getCbomVersions(cbom.getSecuredUuid());

        // Then
        assertNotNull(versions);
        assertEquals(1, versions.size());
        assertEquals(serialNumber, versions.getFirst().getSerialNumber());
        assertEquals(1, versions.getFirst().getVersion());
    }

    @Test
    void testGetCbomVersions_MultipleSerialNumbers() throws NotFoundException {
        // Given
        String serialNumber1 = "urn:uuid:test-456";
        String serialNumber2 = "urn:uuid:test-789";

        Cbom cbom1 = new Cbom();
        cbom1.setSerialNumber(serialNumber1);
        cbom1.setVersion(1);
        cbom1.setSpecVersion("1.6");
        cbom1.setTimestamp(OffsetDateTime.now());
        cbomRepository.save(cbom1);

        Cbom cbom2 = new Cbom();
        cbom2.setSerialNumber(serialNumber2);
        cbom2.setVersion(1);
        cbom2.setSpecVersion("1.6");
        cbom2.setTimestamp(OffsetDateTime.now());
        cbomRepository.save(cbom2);

        // When
        List<CbomDto> versions = cbomService.getCbomVersions(cbom1.getSecuredUuid());

        // Then
        assertNotNull(versions);
        assertEquals(1, versions.size());
        assertEquals(serialNumber1, versions.get(0).getSerialNumber());
    }

    @Test
    void testUploadCbom_MissingContent() {
        // Given - null content
        CbomUploadRequestDto request = new CbomUploadRequestDto();

        // When / Then
        assertThrows(ValidationException.class, () ->
            cbomService.createCbom(request)
        );
    }

    @Test
    void testUploadCbom_MissingSerialNumber() {
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
    void testUploadCbom_MissingVersion() {
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
    void testUploadCbom_VersionNotAnInteger() {
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
    void testUploadCbom_MissingSpecVersion() {
        // Given
        Map<String, Object> content = new HashMap<>();
        content.put("serialNumber", "urn:uuid:test-123");
        content.put("bomFormat", "CycloneDX");
        // Missing specVersion
        content.put("version", "1");

        CbomUploadRequestDto request = new CbomUploadRequestDto();
        request.setContent(content);

        // When / Then
        assertThrows(ValidationException.class, () ->
            cbomService.createCbom(request)
        );
    }

    @Test
    void testUploadCbom_MissingMetadata() {
        // Given
        Map<String, Object> content = new HashMap<>();
        content.put("serialNumber", "urn:uuid:test-123");
        content.put("bomFormat", "CycloneDX");
        content.put("specVersion", "1.5");
        content.put("version", "1");
        // Missing metadata

        CbomUploadRequestDto request = new CbomUploadRequestDto();
        request.setContent(content);

        // When / Then
        ValidationException exception = assertThrows(ValidationException.class, () ->
            cbomService.createCbom(request)
        );
        assertEquals("metadata must be present", exception.getMessage());
    }

    @Test
    void testUploadCbom_MetadataNotObject() {
        // Given
        Map<String, Object> content = new HashMap<>();
        content.put("serialNumber", "urn:uuid:test-123");
        content.put("bomFormat", "CycloneDX");
        content.put("specVersion", "1.5");
        content.put("version", "1");
        content.put("metadata", "not an object"); // String instead of Map

        CbomUploadRequestDto request = new CbomUploadRequestDto();
        request.setContent(content);

        // When / Then
        ValidationException exception = assertThrows(ValidationException.class, () ->
            cbomService.createCbom(request)
        );
        assertEquals("metadata must be JSON object", exception.getMessage());
    }

    @Test
    void testUploadCbom_MissingTimestamp() {
        // Given
        Map<String, Object> content = new HashMap<>();
        content.put("serialNumber", "urn:uuid:test-123");
        content.put("bomFormat", "CycloneDX");
        content.put("specVersion", "1.5");
        content.put("version", "1");

        Map<String, Object> metadata = new HashMap<>();
        // Missing timestamp
        content.put("metadata", metadata);

        CbomUploadRequestDto request = new CbomUploadRequestDto();
        request.setContent(content);

        // When / Then
        ValidationException exception = assertThrows(ValidationException.class, () ->
            cbomService.createCbom(request)
        );
        assertEquals("metadata.timestamp must be present", exception.getMessage());
    }

    @Test
    void testUploadCbom_InvalidTimestampFormat() {
        // Given
        Map<String, Object> content = new HashMap<>();
        content.put("serialNumber", "urn:uuid:test-123");
        content.put("bomFormat", "CycloneDX");
        content.put("specVersion", "1.5");
        content.put("version", "1");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", "not-a-valid-timestamp");
        content.put("metadata", metadata);

        CbomUploadRequestDto request = new CbomUploadRequestDto();
        request.setContent(content);

        // When / Then
        ValidationException exception = assertThrows(ValidationException.class, () ->
            cbomService.createCbom(request)
        );
        assertEquals("metadata.timestamp must be valid ISO-8601 timestamp", exception.getMessage());
    }

    @Test
    void testUploadCbom_TimestampNotAString() {
        // Given
        Map<String, Object> content = new HashMap<>();
        content.put("serialNumber", "urn:uuid:test-123");
        content.put("bomFormat", "CycloneDX");
        content.put("specVersion", "1.5");
        content.put("version", "1");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", 42);
        content.put("metadata", metadata);

        CbomUploadRequestDto request = new CbomUploadRequestDto();
        request.setContent(content);

        // When / Then
        ValidationException exception = assertThrows(ValidationException.class, () ->
            cbomService.createCbom(request)
        );
        assertEquals("timestamp must be String", exception.getMessage());
    }

    @Test
    void testCreateCbom_AlreadyExists_409Response() {
        // Given
        String serialNumber = "urn:uuid:test-123";
        Integer version = 1;
        OffsetDateTime timestamp = OffsetDateTime.now();

        Map<String, Object> content = new HashMap<>();
        content.put("serialNumber", serialNumber);
        content.put("bomFormat", "CycloneDX");
        content.put("specVersion", "1.5");
        content.put("version", version);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", timestamp.toString());
        content.put("metadata", metadata);

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
    void testCreateCbom_RepositoryServerError_500() {
        // Given
        String serialNumber = "urn:uuid:server-error";
        Integer version = 1;
        OffsetDateTime timestamp = OffsetDateTime.now();

        Map<String, Object> content = new HashMap<>();
        content.put("serialNumber", serialNumber);
        content.put("bomFormat", "CycloneDX");
        content.put("specVersion", "1.5");
        content.put("version", version);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", timestamp.toString());
        content.put("metadata", metadata);

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
    void testCreateCbom_BadRequest_400() {
        // Given
        String serialNumber = "urn:uuid:bad-request";
        Integer version = 1;
        OffsetDateTime timestamp = OffsetDateTime.now();

        Map<String, Object> content = new HashMap<>();
        content.put("serialNumber", serialNumber);
        content.put("specVersion", "1.5");
        content.put("version", version);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", timestamp.toString());
        content.put("metadata", metadata);

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
        OffsetDateTime timestamp = OffsetDateTime.now();

        request1.setContent(Map.of(
            "serialNumber", "urn:uuid:first",
            "version", 1,
            "specVersion", "1.6",
            "metadata", Map.of("timestamp", timestamp.toString()
        )));

        CbomUploadRequestDto request2 = new CbomUploadRequestDto();
        request2.setContent(Map.of(
            "serialNumber", "urn:uuid:second",
            "version", 1,
            "specVersion", "1.6",
            "metadata", Map.of("timestamp", timestamp.toString()
        )));

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

    @Test
    void testGetSearchableFieldInformationByGroup() {
        // given
        List<SearchFieldDataByGroupDto> attributeFields = new ArrayList<>();
        Mockito.when(attributeEngine.getResourceSearchableFields(Resource.CBOM, false))
                .thenReturn(attributeFields);

        // when
        List<SearchFieldDataByGroupDto> result = cbomService.getSearchableFieldInformationByGroup();

        // then
        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Verify attributeEngine was called
        Mockito.verify(attributeEngine).getResourceSearchableFields(Resource.CBOM, false);

        // Get the last group (which should be the property group we added)
        SearchFieldDataByGroupDto propertyGroup = result.get(result.size() - 1);

        assertNotNull(propertyGroup);
        assertEquals(9, propertyGroup.getSearchFieldData().size());

        // Verify all expected fields are present
        List<String> fieldNames = propertyGroup.getSearchFieldData().stream()
                .map(SearchFieldDataDto::getFieldIdentifier)
                .toList();

        assertTrue(fieldNames.contains(FilterField.CBOM_SERIAL_NUMBER.name()));
        assertTrue(fieldNames.contains(FilterField.CBOM_VERSION.name()));
        assertTrue(fieldNames.contains(FilterField.CBOM_TIMESTAMP.name()));
        assertTrue(fieldNames.contains(FilterField.CBOM_SOURCE.name()));
        assertTrue(fieldNames.contains(FilterField.CBOM_ALGORITHMS_COUNT.name()));
        assertTrue(fieldNames.contains(FilterField.CBOM_CERTIFICATES_COUNT.name()));
        assertTrue(fieldNames.contains(FilterField.CBOM_PROTOCOLS_COUNT.name()));
        assertTrue(fieldNames.contains(FilterField.CBOM_CRYPTO_MATERIAL_COUNT.name()));
        assertTrue(fieldNames.contains(FilterField.CBOM_TOTAL_ASSETS_COUNT.name()));
    }
}
