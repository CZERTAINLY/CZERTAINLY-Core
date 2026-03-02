package com.czertainly.core.auth.oauth2;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.logging.enums.OperationResult;
import com.czertainly.api.model.core.settings.authentication.AuthenticationSettingsDto;
import com.czertainly.api.model.core.settings.authentication.OAuth2ProviderSettingsDto;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.core.security.authn.CzertainlyAuthenticationException;
import com.czertainly.core.service.AuditLogService;
import com.czertainly.core.settings.SettingsCache;
import com.czertainly.core.util.OAuth2Constants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Controller
public class LoginController {

    private AuditLogService auditLogService;

    @Autowired
    public void setAuditLogService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping(
            value = "/login",
            produces = {"application/json"}
    )
    @ResponseBody
    public ResponseEntity<List<LoginProviderDto>> login(@RequestParam(value = "redirect", required = false) String redirectUrl, HttpServletRequest request, HttpServletResponse response, @RequestParam(value = "error", required = false) String error) {

        request.getSession().setAttribute(OAuth2Constants.SERVLET_CONTEXT_SESSION_ATTRIBUTE, ServletUriComponentsBuilder.fromCurrentContextPath().build().getPath());

        if (error != null) {
            request.getSession().invalidate();
            throw new CzertainlyAuthenticationException("Error during authentication: " + error);
        }

        String baseUrl = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .replacePath(null)
                .build()
                .toUriString();

        String validatedRedirectUrl = validateAndNormalizeRedirect(redirectUrl);
        if (validatedRedirectUrl != null) {
            request.getSession().setAttribute(OAuth2Constants.REDIRECT_URL_SESSION_ATTRIBUTE, baseUrl + validatedRedirectUrl);
        } else {
            throw new CzertainlyAuthenticationException("No redirect URL provided for login or redirect URL is invalid");
        }

        AuthenticationSettingsDto authenticationSettings = SettingsCache.getSettings(SettingsSection.AUTHENTICATION);

        // Display only properly configured providers
        List<OAuth2ProviderSettingsDto> oauth2Providers = authenticationSettings.getOAuth2Providers() != null 
                ? authenticationSettings.getOAuth2Providers().values().stream().filter(this::validOAuth2Provider).toList()
                : List.of();
        if (oauth2Providers.size() == 1) {
            request.getSession().setMaxInactiveInterval(oauth2Providers.getFirst().getSessionMaxInactiveInterval());
            String redirectPath = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/oauth2/authorization/{provider}")
                    .buildAndExpand(oauth2Providers.getFirst().getName())
                    .encode()
                    .toUriString();
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(redirectPath));
            return new ResponseEntity<>(null, headers, HttpStatus.FOUND);
        }

        List<LoginProviderDto> loginProviders = oauth2Providers.stream()
                .map(provider -> {
                    LoginProviderDto loginProvider = new LoginProviderDto();
                    loginProvider.setName(provider.getName());
                    String loginUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                            .path("/oauth2/authorization/{provider}/prepare")
                            .buildAndExpand(provider.getName())
                            .encode()
                            .toUriString();
                    loginProvider.setLoginUrl(loginUrl);
                    return loginProvider;
                })
                .toList();

        return new ResponseEntity<>(loginProviders, HttpStatus.OK);
    }

    @GetMapping("/oauth2/authorization/{provider}/prepare")
    public void loginWithProvider(@PathVariable String provider, @RequestParam(value = "redirect", required = false) String redirect, HttpServletResponse response, HttpServletRequest request) throws IOException {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .replacePath(null)
                .build()
                .toUriString();

        String validatedRedirectUrl = validateAndNormalizeRedirect(redirect);
        if (validatedRedirectUrl != null) {
            request.getSession(true).setAttribute(OAuth2Constants.REDIRECT_URL_SESSION_ATTRIBUTE, baseUrl + validatedRedirectUrl);
        }

        AuthenticationSettingsDto authenticationSettings = SettingsCache.getSettings(SettingsSection.AUTHENTICATION);
        OAuth2ProviderSettingsDto providerSettings = authenticationSettings.getOAuth2Providers() != null 
                ? authenticationSettings.getOAuth2Providers().get(provider)
                : null;

        if (providerSettings == null) {
            String accessToken;
            try {
                OAuth2AccessToken oauth2AccessToken = (OAuth2AccessToken) request.getSession().getAttribute(OAuth2Constants.ACCESS_TOKEN_SESSION_ATTRIBUTE);
                accessToken = oauth2AccessToken.getTokenValue();
            } catch (Exception e) {
                accessToken = null;
            }

            String message = "Unknown OAuth2 Provider with name '%s' for authentication with OAuth2 flow".formatted(provider);
            auditLogService.logAuthentication(Operation.LOGIN, OperationResult.FAILURE, message, accessToken);
            throw new CzertainlyAuthenticationException(message);
        }

        if (request.getSession(false) == null || request.getSession().getAttribute(OAuth2Constants.REDIRECT_URL_SESSION_ATTRIBUTE) == null) {
            throw new CzertainlyAuthenticationException("Missing redirect URL. Please start the login from the beginning.");
        }

        request.getSession().setMaxInactiveInterval(providerSettings.getSessionMaxInactiveInterval());
        response.sendRedirect(ServletUriComponentsBuilder.fromCurrentContextPath().build().getPath() + "/oauth2/authorization/" + provider);
    }

    @GetMapping("/oauth2/{provider}/jwkSet")
    public ResponseEntity<String> getJwkSet(@PathVariable String provider) {
        AuthenticationSettingsDto authenticationSettings = SettingsCache.getSettings(SettingsSection.AUTHENTICATION);

        if (authenticationSettings.getOAuth2Providers() == null || authenticationSettings.getOAuth2Providers().get(provider) == null) {
            throw new ValidationException("Provider %s does not exist.".formatted(provider));
        }

        String jwkSetEncoded = authenticationSettings.getOAuth2Providers().get(provider).getJwkSet();
        if (jwkSetEncoded == null) {
            throw new ValidationException("Provider %s does not have JWK Set set up.".formatted(provider));
        }

        String jwkSet = new String(Base64.getDecoder().decode(jwkSetEncoded));
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", MediaType.APPLICATION_JSON_VALUE);
        return new ResponseEntity<>(jwkSet, headers, HttpStatus.OK);
    }


    private boolean validOAuth2Provider(OAuth2ProviderSettingsDto settingsDto) {
        return (settingsDto.getClientId() != null) &&
                (settingsDto.getClientSecret() != null) &&
                (settingsDto.getAuthorizationUrl() != null) &&
                (settingsDto.getTokenUrl() != null) &&
                (settingsDto.getJwkSetUrl() != null || settingsDto.getJwkSet() != null) &&
                (settingsDto.getLogoutUrl() != null) &&
                (settingsDto.getPostLogoutUrl() != null);
    }

    private String validateAndNormalizeRedirect(String redirectUrl) {
        if (redirectUrl == null || redirectUrl.isEmpty()) {
            return null;
        }

        // Must be a relative path (starts with /) and not protocol-relative (not starting with //)
        if (!redirectUrl.startsWith("/") || redirectUrl.startsWith("//")) {
            return null;
        }

        // Basic normalization to remove host/scheme if any (though startsWith("/") already helps)
        try {
            URI uri = URI.create(redirectUrl);
            if (uri.isAbsolute() || uri.getHost() != null) {
                return null;
            }
            return uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "") + (uri.getFragment() != null ? "#" + uri.getFragment() : "");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
