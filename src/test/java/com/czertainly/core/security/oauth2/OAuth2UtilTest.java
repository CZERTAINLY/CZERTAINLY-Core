package com.czertainly.core.security.oauth2;


import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.api.model.core.settings.authentication.AuthenticationSettingsDto;
import com.czertainly.api.model.core.settings.authentication.OAuth2ProviderSettingsDto;
import com.czertainly.core.security.authn.CzertainlyAuthenticationException;
import com.czertainly.core.settings.SettingsCache;
import com.czertainly.core.util.OAuth2Util;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.nimbusds.jose.JOSEException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.session.Session;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2UtilTest {

    @Test
    void testValidateAudiences() throws NoSuchAlgorithmException, JOSEException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        OAuth2ProviderSettingsDto providerSettingsDto = new OAuth2ProviderSettingsDto();
        OAuth2AccessToken accessTokenCorrectAudience = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, OAuth2TestUtil.createJwtTokenValue(keyPair.getPrivate(), 1, null, "expected", ""), Instant.now(), Instant.now().plusMillis(200));
        OAuth2AccessToken accessTokenIncorrectAudience = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, OAuth2TestUtil.createJwtTokenValue(keyPair.getPrivate(), 1, null, "unexpected", ""), Instant.now(), Instant.now().plusMillis(200));


        providerSettingsDto.setAudiences(List.of("expected"));
        Assertions.assertDoesNotThrow(() -> OAuth2Util.validateAudiences(accessTokenCorrectAudience, providerSettingsDto));

        providerSettingsDto.setAudiences(List.of("expected", "other"));
        Assertions.assertDoesNotThrow(() -> OAuth2Util.validateAudiences(accessTokenCorrectAudience, providerSettingsDto));

        Assertions.assertThrows(CzertainlyAuthenticationException.class, () -> OAuth2Util.validateAudiences(accessTokenIncorrectAudience, providerSettingsDto));

        providerSettingsDto.setAudiences(new ArrayList<>());
        Assertions.assertDoesNotThrow(() -> OAuth2Util.validateAudiences(accessTokenIncorrectAudience, providerSettingsDto));

    }

    @Test
    void testEndUserSession_SuccessfulLogout() {
        // Mocks
        Session session = mock(Session.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        OAuth2AuthenticationToken authToken = mock(OAuth2AuthenticationToken.class);
        DefaultOidcUser oidcUser = mock(DefaultOidcUser.class);
        AuthenticationSettingsDto authSettings = mock(AuthenticationSettingsDto.class);
        OAuth2ProviderSettingsDto providerSettings = mock(OAuth2ProviderSettingsDto.class);

        // Prepare session returns security context
        when(session.getAttribute("SPRING_SECURITY_CONTEXT")).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authToken);

        // Prepare token and provider mocks
        when(authToken.getAuthorizedClientRegistrationId()).thenReturn("test-client");
        when(authToken.getPrincipal()).thenReturn(oidcUser);
        when(authToken.getName()).thenReturn("my-user");
        when(oidcUser.getIdToken()).thenReturn(mock(org.springframework.security.oauth2.core.oidc.OidcIdToken.class));
        when(oidcUser.getIdToken().getTokenValue()).thenReturn("id-token-value");

        // Prepare AuthenticationSettingsDto and OAuth2ProviderSettingsDto
        WireMockServer mockServer = new WireMockServer(0);
        mockServer.start();
        Map<String, OAuth2ProviderSettingsDto> providers = new HashMap<>();
        providers.put("test-client", providerSettings);
        when(authSettings.getOAuth2Providers()).thenReturn(providers);
        when(providerSettings.getLogoutUrl()).thenReturn("http://localhost:" + mockServer.port());
        when(providerSettings.getName()).thenReturn("TestProvider");

        // Mock static SettingsCache
        try (MockedStatic<SettingsCache> settingsCacheMock = mockStatic(SettingsCache.class)) {
            settingsCacheMock.when(() -> SettingsCache.getSettings(SettingsSection.AUTHENTICATION))
                    .thenReturn(authSettings);
            // Not mocked endpoint
            Assertions.assertDoesNotThrow(() -> OAuth2Util.endUserSession(session));

            WireMock.configureFor("localhost", mockServer.port());
            mockServer.stubFor(
                    WireMock.get(WireMock.urlPathEqualTo("/"))
                            .withQueryParam("id_token_hint", WireMock.matching(".*"))
                            .willReturn(WireMock.aResponse().withStatus(200))
            );
            // Mocked endpoint
            Assertions.assertDoesNotThrow(() -> OAuth2Util.endUserSession(session));
        }
    }



}
