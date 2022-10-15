package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.model.client.connector.ConnectorRequestDto;
import com.czertainly.api.model.core.connector.AuthType;
import com.czertainly.core.util.AuthenticationTokenTestHelper;
import com.czertainly.core.util.BaseSpringBootTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

public class ConnectorRegistrationServiceTest extends BaseSpringBootTest {

    @Autowired
    private ConnectorRegistrationService connectorRegistrationService;

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
    public void testRegisterConnector() throws ConnectorException, AlreadyExistException {
        mockServer.stubFor(WireMock.get("/v1").willReturn(WireMock.okJson("[]")));
        ConnectorRequestDto request = new ConnectorRequestDto();
        request.setName("testConnector");
        request.setAuthType(AuthType.NONE);
        request.setUrl("localhost:3665");
        connectorRegistrationService.registerConnector(request);
    }

    @Override
    protected Authentication getAuthentication() {
        return AuthenticationTokenTestHelper.getAnonymousToken("anonymousUser");
    }
}
