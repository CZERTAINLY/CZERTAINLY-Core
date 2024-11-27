package com.czertainly.core.security.oauth2;

import com.czertainly.core.auth.oauth2.CzertainlyJwtAuthenticationConverter;
import com.czertainly.core.util.BaseSpringBootTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public class CzertainlyJwtConvertorTest extends BaseSpringBootTest {

    @DynamicPropertySource
    static void authServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("auth-service.base-url", () -> "http://localhost:10004");
    }

    @Autowired
    CzertainlyJwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void testJwtConverter() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("username", "user")
                .claim("roles", "role")
                .claim("random", "random").build();

        WireMockServer authMockServer = new WireMockServer(10004);
        authMockServer.start();
        WireMock.configureFor("localhost", authMockServer.port());

        authMockServer.stubFor(WireMock.post(WireMock.urlPathMatching("/auth")).willReturn(
                WireMock.okJson("""        
                        {
                          "authenticated": true,
                          "data": {
                            "user": {
                              "username": "user2"
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
                        """)
        ));

        Assertions.assertDoesNotThrow(() -> jwtAuthenticationConverter.convert(jwt));
        authMockServer.stop();
    }
}

