package com.czertainly.core.security.oauth2;

import com.czertainly.api.model.core.settings.OAuth2ProviderSettingsDto;
import com.czertainly.core.auth.oauth2.CzertainlyClientRegistrationRepository;
import com.czertainly.core.auth.oauth2.CzertainlyJwtAuthenticationConverter;
import com.czertainly.core.service.SettingService;
import com.czertainly.core.util.BaseSpringBootTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

@SpringBootTest
class OAuth2Test extends BaseSpringBootTest {

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private SettingService settingService;

    @Autowired
    private CzertainlyClientRegistrationRepository clientRegistrationRepository;

    private KeyPair keyPair;

    private static final String ISSUER_URL = "http://localhost:8082/realms/CZERTAINLY-realm";

    private OAuth2ProviderSettingsDto providerSettings;

    String tokenValue;

    CzertainlyJwtAuthenticationConverter jwtAuthenticationConverter;

    WireMockServer mockServer;

    @DynamicPropertySource
    static void authServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("auth-service.base-url", () -> "http://localhost:10004");
    }

    @Autowired
    public void setJwtAuthenticationConverter(CzertainlyJwtAuthenticationConverter jwtAuthenticationConverter) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException, JsonProcessingException, JOSEException {
        mockServer = new WireMockServer(8082);
        mockServer.start();
        WireMock.configureFor("localhost", mockServer.port());

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        mockServer.stubFor(WireMock.get("/realms/CZERTAINLY-realm/.well-known/openid-configuration")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("""
                                {
                                  "issuer": "%s",
                                  "authorization_endpoint": "%s/protocol/openid-connect/auth",
                                  "token_endpoint": "%s/protocol/openid-connect/token",
                                  "jwks_uri": "%s/protocol/openid-connect/certs",
                                  "grant_types_supported": ["authorization_code", "implicit", "refresh_token"]
                                }
                                """, ISSUER_URL, ISSUER_URL, ISSUER_URL, ISSUER_URL))));
        mockServer.stubFor(WireMock.get("/realms/CZERTAINLY-realm/protocol/openid-connect/certs")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[" + convertRSAPrivateKeyToJWK((RSAPublicKey) keyPair.getPublic()) + "]}")));


        providerSettings = new OAuth2ProviderSettingsDto();
        providerSettings.setClientSecret("secret");
        providerSettings.setIssuerUrl(ISSUER_URL);
        providerSettings.setClientId("client");
        providerSettings.setClientSecret("secret");
        providerSettings.setAuthorizationUrl("http");
        providerSettings.setTokenUrl("http");
        settingService.updateOAuth2ProviderSettings("provider", providerSettings);

        tokenValue = createJwtTokenValue(keyPair.getPrivate(), 3600 * 1000);

    }

    @AfterEach
    void stopServer() {
        mockServer.stop();
    }

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
    }

    @Test
    void testJwtDecoderOnValidTokenWithoutAudiences() throws JOSEException {
        SecurityContextHolder.clearContext();
        Assertions.assertInstanceOf(Jwt.class, jwtDecoder.decode(tokenValue));

        String almostExpiredToken = createJwtTokenValue(keyPair.getPrivate(), 1);
        // Test if 30 s skew is added to the time and therefore the token should be successfully validated
        Assertions.assertInstanceOf(Jwt.class, jwtDecoder.decode(almostExpiredToken));
    }

    @Test
    void testJwtDecoderOnExpiredTokenWithoutAudiences() throws JOSEException {
        providerSettings.setSkew(0);
        settingService.updateOAuth2ProviderSettings("provider", providerSettings);

        SecurityContextHolder.clearContext();
        String expiredToken = createJwtTokenValue(keyPair.getPrivate(), 1);
        Assertions.assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(expiredToken));
    }

    @Test
    void testJwtDecoderOnTokenWithValidAudiences() {
        providerSettings.setAudiences(List.of("your-audience"));
        settingService.updateOAuth2ProviderSettings("provider", providerSettings);

        SecurityContextHolder.clearContext();
        Assertions.assertInstanceOf(Jwt.class, jwtDecoder.decode(tokenValue));
    }

    @Test
    void testJwtDecoderOnTokenWithInvalidAudiences() {
        providerSettings.setAudiences(List.of("different-audience"));
        settingService.updateOAuth2ProviderSettings("provider", providerSettings);
        SecurityContextHolder.clearContext();
        Assertions.assertThrows(JwtValidationException.class, () -> jwtDecoder.decode(tokenValue));
    }

    @Test
    void testRetrieveClientRegistration() {
        Assertions.assertNotNull(clientRegistrationRepository.findByRegistrationId("provider"));
    }

    private String createJwtTokenValue(PrivateKey privateKey, int expiryInMilliseconds) throws JOSEException {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("your-subject")
                .audience("your-audience")
                .expirationTime(new Date(System.currentTimeMillis() + expiryInMilliseconds)) // 1 hour
                .claim("username", "username")
                .issuer(ISSUER_URL)
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);
        JWSSigner signer = new RSASSASigner(privateKey);
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }

    private String convertRSAPrivateKeyToJWK(RSAPublicKey publicKey) throws JsonProcessingException {
        BigInteger modulus = publicKey.getModulus();
        BigInteger publicExponent = publicKey.getPublicExponent();

        String n = Base64.getEncoder().encodeToString(modulus.toByteArray());
        String e = Base64.getEncoder().encodeToString(publicExponent.toByteArray());

        Map<String, Object> jwk = new HashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("n", n);
        jwk.put("e", e);
        jwk.put("alg", "RS256");
        jwk.put("use", "sig");

        return new ObjectMapper().writeValueAsString(jwk);
    }


}