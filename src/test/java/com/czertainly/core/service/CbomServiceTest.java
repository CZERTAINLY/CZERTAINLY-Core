package com.czertainly.core.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.cbom.client.CbomRepositoryClient;
import com.czertainly.core.util.BaseSpringBootTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

class CbomServiceTest extends BaseSpringBootTest {

    @Autowired
    private CbomService cbomService;

    @Autowired
    private AttributeEngine attributeEngine;

    private WireMockServer mockServer;

    private WireMockServer mockCbomRepository;

    private WebClient webClient;
    private CbomRepositoryClient cbomRepositoryClient;

    @BeforeEach
    public void setUp() {
        mockServer = new WireMockServer(0);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        mockCbomRepository = new WireMockServer(0);
        mockCbomRepository.start();

        webClient = WebClient.builder()
            .baseUrl("http://localhost:" + mockServer.port())
            .build();
        cbomRepositoryClient = new CbomRepositoryClient();
        ReflectionTestUtils.setField(cbomRepositoryClient, "client", webClient);
    }

    @AfterEach
    public void tearDown() {
        mockServer.stop();
    }
}
