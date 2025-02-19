package com.czertainly.core.security.oauth2;

import com.czertainly.core.util.BaseSpringBootTestNoAuth;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@AutoConfigureMockMvc
@SpringBootTest
class SecurityConfigTest extends BaseSpringBootTestNoAuth {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private JwtDecoder jwtDecoder;


    @DynamicPropertySource
    static void authServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("auth-service.base-url", () -> "http://localhost:10003");
    }

    private static final String CERTIFICATE_USER_USERNAME = "certificate-user";
    private static final String CERTIFICATE_HEADER_VALUE = "certificate";
    private static final String TOKEN = "mock-token";
    private static final String TOKEN_HEADER_VALUE = "Bearer " + TOKEN;
    private static final String TOKEN_USER_USERNAME = "token-user";

    Jwt mockJwt;
    WireMockServer mockServer;

    @BeforeEach
    void setUp() {
        mockServer = new WireMockServer(10003);
        mockServer.start();
        WireMock.configureFor("localhost", mockServer.port());

        String certificateUserUuid = UUID.randomUUID().toString();

        mockServer.stubFor(WireMock.post(WireMock.urlPathMatching("/auth"))
                .withRequestBody(WireMock.containing(CERTIFICATE_HEADER_VALUE))
                .willReturn(
                        WireMock.okJson(String.format("""        
                                {
                                  "authenticated": true,
                                  "data": {
                                    "user": {
                                      "uuid": "%s",
                                      "username": "%s"
                                    },
                                    "roles": [
                                      {
                                        "name": "superadmin"
                                      }
                                    ],
                                    "permissions": {
                                      "allowAllResources": true,
                                      "resources": []
                                    }
                                  }
                                }
                                """, certificateUserUuid, CERTIFICATE_USER_USERNAME)
                        )
                ));

        mockServer.stubFor(WireMock.post(WireMock.urlPathMatching("/auth"))
                .withRequestBody(WireMock.containing("localhost"))
                .willReturn(
                        WireMock.okJson("""        
                                {
                                  "authenticated": null,
                                  "data": null
                                }
                                """
                        )
                ));


        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/auth/users/" + certificateUserUuid)).willReturn(
                WireMock.okJson(String.format("{ \"username\": \"%s\", \"roles\": [{\"name\": \"superadmin\"}]}", CERTIFICATE_USER_USERNAME))
        ));

        String tokenUserUuid = UUID.randomUUID().toString();
        mockJwt = Jwt.withTokenValue(TOKEN)
                .header("alg", "RS256")
                .claim("username", TOKEN_USER_USERNAME)
                .issuer("http://issuer")
                .build();
        mockServer.stubFor(WireMock.post(WireMock.urlPathMatching("/auth"))
                .withRequestBody(WireMock.containing("{\"iss\":\"http://issuer\",\"username\":\"token-user\"}"))
                .willReturn(
                        WireMock.okJson(String.format("""        
                                {
                                  "authenticated": true,
                                  "data": {
                                    "user": {
                                      "uuid": "%s",
                                      "username": "%s"
                                    },
                                    "roles": [
                                      {
                                        "name": "superadmin"
                                      }
                                    ],
                                    "permissions": {
                                      "allowAllResources": true,
                                      "resources": []
                                    }
                                  }
                                }
                                """, tokenUserUuid, TOKEN_USER_USERNAME)
                        )
                ));

        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/auth/users/" + tokenUserUuid)).willReturn(
                WireMock.okJson(String.format("{ \"username\": \"%s\", \"roles\": [{\"name\": \"superadmin\"}]}", TOKEN_USER_USERNAME))
        ));

    }

    @AfterEach
    void afterEach() {
        mockServer.stop();
    }

    @Test
    void authorizeUsingCertificate() throws Exception {
        MvcResult result = mvc.perform(get(ServletUriComponentsBuilder.fromCurrentContextPath().build().getPath() + "/v1/auth/profile")
                .header("X-APP-CERTIFICATE", CERTIFICATE_HEADER_VALUE)).andReturn();
        Assertions.assertTrue(result.getResponse().getContentAsString().contains(CERTIFICATE_USER_USERNAME));
    }

    @Test
    void authorizeProtocolEndpoint() throws Exception {
        mvc.perform(get(ServletUriComponentsBuilder.fromCurrentContextPath().build().getPath() + "/v1/protocols/acme/raProfile/RA Profile/directory")).andExpect(status().isBadRequest());
    }

    @Test
    void authorizeUsingJwtToken() throws Exception {
        Mockito.when(jwtDecoder.decode(TOKEN)).thenReturn(mockJwt);
        MvcResult result = mvc.perform(get(ServletUriComponentsBuilder.fromCurrentContextPath().build().getPath() + "/v1/auth/profile")
                .header("Authorization", TOKEN_HEADER_VALUE)).andReturn();
        Assertions.assertTrue(result.getResponse().getContentAsString().contains(TOKEN_USER_USERNAME));

    }

    @Test
    void authorizeWithCertificateAndToken() throws Exception {
        Mockito.when(jwtDecoder.decode(TOKEN)).thenReturn(null);
        MvcResult result = mvc.perform(get(ServletUriComponentsBuilder.fromCurrentContextPath().build().getPath() + "/v1/auth/profile")
                .header("X-APP-CERTIFICATE", CERTIFICATE_HEADER_VALUE)
                .header("Authorization", TOKEN_HEADER_VALUE)).andReturn();
        Assertions.assertTrue(result.getResponse().getContentAsString().contains(CERTIFICATE_USER_USERNAME));
    }


}
