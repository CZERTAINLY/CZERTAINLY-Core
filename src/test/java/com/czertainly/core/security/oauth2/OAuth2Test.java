package com.czertainly.core.security.oauth2;

import com.czertainly.api.model.core.settings.OAuth2ProviderSettings;
import com.czertainly.core.auth.oauth2.CzertainlyJwtAuthenticationConverter;
import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

@SpringBootTest
@RunWith(SpringRunner.class)
class OAuth2Test extends BaseSpringBootTest {

    @DynamicPropertySource
    static void authServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("auth-service.base-url", () -> "http://localhost:10001");
    }

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private SettingService settingService;

    private KeyPair keyPair;

    private final String ISSUER_URL = "http://localhost:8081/realms/CZERTAINLY-realm";

    private OAuth2ProviderSettings providerSettings;

    String tokenValue;

    @Autowired
    CzertainlyJwtAuthenticationConverter jwtAuthenticationConverter;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException, JsonProcessingException, JOSEException {
        WireMockServer mockServer = new WireMockServer(8081);
        mockServer.start();
        WireMock.configureFor("localhost", mockServer.port());

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        mockServer.stubFor(WireMock.get("/realms/CZERTAINLY-realm/.well-known/openid-configuration")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "issuer": "http://localhost:8081/realms/CZERTAINLY-realm",
                                  "authorization_endpoint": "http://localhost:8081/realms/CZERTAINLY-realm/protocol/openid-connect/auth",
                                  "token_endpoint": "http://localhost:8081/realms/CZERTAINLY-realm/protocol/openid-connect/token",
                                  "jwks_uri": "http://localhost:8081/realms/CZERTAINLY-realm/protocol/openid-connect/certs",
                                  "grant_types_supported": ["authorization_code", "implicit", "refresh_token"]
                                }
                                """)));
        mockServer.stubFor(WireMock.get("/realms/CZERTAINLY-realm/protocol/openid-connect/certs")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[" + convertRSAPrivateKeyToJWK((RSAPublicKey) keyPair.getPublic()) + "]}")));


        providerSettings = new OAuth2ProviderSettings();
        providerSettings.setClientSecret("secret");
        providerSettings.setIssuerUrl(ISSUER_URL);
        settingService.updateOAuth2ProviderSettings("provider", providerSettings);

        tokenValue = createJwtTokenValue(keyPair.getPrivate(), 3600 * 1000);

    }

    @Test
    void testJwtConverter() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("username", "user").build();

        WireMockServer mockServer = new WireMockServer(10001);
        mockServer.start();
        WireMock.configureFor("localhost", mockServer.port());

        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/auth/users/[^/]+")).willReturn(
                WireMock.okJson("{ \"username\": \"user\"}")
        ));

        CzertainlyAuthenticationToken authenticationToken = (CzertainlyAuthenticationToken) jwtAuthenticationConverter.convert(jwt);

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
