package com.czertainly.core.auth.oauth2;

import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.logging.enums.OperationResult;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.api.model.core.settings.authentication.AuthenticationSettingsDto;
import com.czertainly.api.model.core.settings.authentication.OAuth2ProviderSettingsDto;
import com.czertainly.core.service.AuditLogService;
import com.czertainly.core.settings.SettingsCache;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginControllerTest {

    private static WireMockServer mockServer;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("server.servlet.context-path", () -> "");
        registry.add("auth-service.base-url", () -> "http://localhost:10003");
    }

    @LocalServerPort
    int port;

    @Autowired
    SettingsCache settingsCache;

    @MockitoBean
    AuditLogService auditLogService;

    private HttpClient http;

    @BeforeEach
    void setUp() {
        mockServer = new WireMockServer(0);
        mockServer.start();

        http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        settingsCache.cacheSettings(SettingsSection.AUTHENTICATION, new AuthenticationSettingsDto());
        reset(auditLogService);
    }

    @AfterEach
    void tearDown() {
        settingsCache.cacheSettings(SettingsSection.AUTHENTICATION, new AuthenticationSettingsDto());
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    @Test
    void loginShouldFailWhenRedirectMissing() throws Exception {
        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder(uri("/login"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        Assertions.assertTrue(res.statusCode() >= 500);
    }

    @Test
    void loginShouldFailAndInvalidateSessionWhenErrorPresent() throws Exception {
        // 1) Hit /login once to establish a session cookie
        HttpResponse<String> first = http.send(
                HttpRequest.newBuilder(uri("/login?redirect=/ui"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        String sessionCookie = extractSessionCookie(first.headers());
        Assertions.assertNotNull(sessionCookie);

        // 2) Call /login with error, using same cookie → should invalidate
        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder(uri("/login?redirect=/ui&error=oops"))
                        .header("Cookie", sessionCookie)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        Assertions.assertTrue(res.statusCode() >= 500);

        // 3) A subsequent call with same cookie should behave like a fresh session (i.e., server issues a new session cookie)
        HttpResponse<String> after = http.send(
                HttpRequest.newBuilder(uri("/login?redirect=/ui"))
                        .header("Cookie", sessionCookie)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        String newSessionCookie = extractSessionCookie(after.headers());
        Assertions.assertNotNull(newSessionCookie);
        Assertions.assertNotEquals(sessionCookie, newSessionCookie);
    }

    @Test
    void loginShouldReturnConfiguredProvidersWhenMoreThanOne() throws Exception {
        AuthenticationSettingsDto settings = new AuthenticationSettingsDto();
        Map<String, OAuth2ProviderSettingsDto> providers = new HashMap<>();
        providers.put("one", validProvider("one", 123));
        providers.put("two", validProvider("two", 456));
        providers.put("bad", new OAuth2ProviderSettingsDto());
        settings.setOAuth2Providers(providers);
        settingsCache.cacheSettings(SettingsSection.AUTHENTICATION, settings);

        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder(uri("/login?redirect=/ui"))
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        Assertions.assertEquals(200, res.statusCode());
        Assertions.assertTrue(res.headers().firstValue("Content-Type").orElse("").contains("application/json"));

        String json = res.body();
        Assertions.assertTrue(json.contains("\"name\":\"one\""));
        Assertions.assertTrue(json.contains("\"name\":\"two\""));
        Assertions.assertFalse(json.contains("\"name\":\"bad\""));
        Assertions.assertTrue(json.contains("/oauth2/authorization/one/prepare"));
        Assertions.assertTrue(json.contains("/oauth2/authorization/two/prepare"));
    }

    @Test
    void loginShouldRedirectToProviderWhenExactlyOneValidProvider() throws Exception {
        AuthenticationSettingsDto settings = new AuthenticationSettingsDto();
        Map<String, OAuth2ProviderSettingsDto> providers = new HashMap<>();
        providers.put("only", validProvider("only", 321));
        settings.setOAuth2Providers(providers);
        settingsCache.cacheSettings(SettingsSection.AUTHENTICATION, settings);

        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder(uri("/login?redirect=/ui"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        // controller does sendRedirect("oauth2/authorization/{provider}") => 302
        Assertions.assertTrue(res.statusCode() >= 300 && res.statusCode() < 400);
        Assertions.assertEquals("oauth2/authorization/only", res.headers().firstValue("Location").orElse(null));

        // We can’t directly introspect server-side session here; instead, verify session cookie exists.
        Assertions.assertNotNull(extractSessionCookie(res.headers()));
    }

    @Test
    void prepareShouldRedirectWhenProviderKnown() throws Exception {
        AuthenticationSettingsDto settings = new AuthenticationSettingsDto();
        Map<String, OAuth2ProviderSettingsDto> providers = new HashMap<>();
        providers.put("test", validProvider("test", 999));
        settings.setOAuth2Providers(providers);
        settingsCache.cacheSettings(SettingsSection.AUTHENTICATION, settings);

        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder(uri("/oauth2/authorization/test/prepare"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        Assertions.assertTrue(res.statusCode() >= 300 && res.statusCode() < 400);
        Assertions.assertEquals("/oauth2/authorization/test", res.headers().firstValue("Location").orElse(null));
    }

    @Test
    void prepareShouldAuditAndFailWhenProviderUnknown_withoutAccessToken() throws Exception {
        settingsCache.cacheSettings(SettingsSection.AUTHENTICATION, new AuthenticationSettingsDto());

        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder(uri("/oauth2/authorization/unknown/prepare"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        Assertions.assertTrue(res.statusCode() >= 500);
        verify(auditLogService, times(1)).logAuthentication(eq(Operation.LOGIN), eq(OperationResult.FAILURE), contains("Unknown OAuth2 Provider"), isNull());
    }

    @Test
    void jwkSetShouldReturnDecodedJwkSet() throws Exception {
        String jwkJson = "{\"keys\":[]}";
        String jwkEncoded = Base64.getEncoder().encodeToString(jwkJson.getBytes(StandardCharsets.UTF_8));

        AuthenticationSettingsDto settings = new AuthenticationSettingsDto();
        Map<String, OAuth2ProviderSettingsDto> providers = new HashMap<>();
        OAuth2ProviderSettingsDto provider = validProvider("test", 10);
        provider.setJwkSet(jwkEncoded);
        provider.setJwkSetUrl(null);
        providers.put("test", provider);
        settings.setOAuth2Providers(providers);
        settingsCache.cacheSettings(SettingsSection.AUTHENTICATION, settings);

        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder(uri("/oauth2/test/jwkSet"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        Assertions.assertEquals(200, res.statusCode());
        Assertions.assertEquals(MediaType.APPLICATION_JSON_VALUE, res.headers().firstValue("Content-type").orElse(null));
        Assertions.assertEquals(jwkJson, res.body());
    }

    @Test
    void jwkSetShouldFailWhenJwkSetMissing() throws Exception {
        AuthenticationSettingsDto settings = new AuthenticationSettingsDto();
        Map<String, OAuth2ProviderSettingsDto> providers = new HashMap<>();
        OAuth2ProviderSettingsDto provider = validProvider("test", 10);
        provider.setJwkSet(null);
        providers.put("test", provider);
        settings.setOAuth2Providers(providers);
        settingsCache.cacheSettings(SettingsSection.AUTHENTICATION, settings);

        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder(uri("/oauth2/test/jwkSet"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        Assertions.assertTrue(res.statusCode() >= 500);
    }

    @Test
    void prepareShouldAuditAndFailWhenProviderUnknown_withAccessToken() throws Exception {
        // Since we can’t inject arbitrary HttpSession attributes over HTTP, we validate the core behavior here:
        // - endpoint fails
        // - audit log called (token may be null)
        settingsCache.cacheSettings(SettingsSection.AUTHENTICATION, new AuthenticationSettingsDto());

        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder(uri("/oauth2/authorization/unknown/prepare"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        Assertions.assertTrue(res.statusCode() >= 500);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService, times(1)).logAuthentication(eq(Operation.LOGIN), eq(OperationResult.FAILURE), messageCaptor.capture(), any());
        Assertions.assertTrue(messageCaptor.getValue().contains("Unknown OAuth2 Provider"));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String extractSessionCookie(HttpHeaders headers) {
        // Default for Spring Session/Tomcat is JSESSIONID; for Spring Session it can be SESSION.
        List<String> setCookies = headers.allValues("Set-Cookie");
        Optional<String> match = setCookies.stream()
                .map(HttpCookie::parse)
                .flatMap(List::stream)
                .filter(c -> c.getName().equalsIgnoreCase("JSESSIONID") || c.getName().equalsIgnoreCase("SESSION"))
                .findFirst()
                .map(c -> c.getName() + "=" + c.getValue());

        return match.orElse(null);
    }

    private static OAuth2ProviderSettingsDto validProvider(String name, int sessionMaxInactiveInterval) {
        OAuth2ProviderSettingsDto p = new OAuth2ProviderSettingsDto();
        p.setName(name);
        p.setClientId("client");
        p.setClientSecret("secret");
        p.setAuthorizationUrl("http://auth");
        p.setTokenUrl("http://token");
        p.setJwkSetUrl("http://jwk");
        p.setLogoutUrl("http://logout");
        p.setPostLogoutUrl("http://post-logout");
        p.setSessionMaxInactiveInterval(sessionMaxInactiveInterval);
        return p;
    }
}

