package com.czertainly.core.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import com.czertainly.api.exception.CbomRepositoryException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.core.cbom.CbomDetailDto;
import com.czertainly.api.model.core.cbom.CbomDto;
import com.czertainly.api.model.core.cbom.CbomListResponseDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.cbom.client.CbomRepositoryClient;
import com.czertainly.core.dao.entity.Cbom;
import com.czertainly.core.dao.repository.CbomRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import com.fasterxml.jackson.databind.ObjectMapper;

class CbomServiceTest extends BaseSpringBootTest {

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
}
