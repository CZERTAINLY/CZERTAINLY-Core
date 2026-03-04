package com.czertainly.core.auth.oauth2;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.LoginController;
import com.czertainly.api.model.core.auth.LoginProviderDto;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.List;

@Controller
@Slf4j
public class LoginControllerImpl implements LoginController {

    private AuditLogService auditLogService;

    @Autowired
    public void setAuditLogService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public List<LoginProviderDto> login(String error) {
        HttpServletRequest request = getHttpServletRequest();
        request.getSession().setAttribute(OAuth2Constants.SERVLET_CONTEXT_SESSION_ATTRIBUTE, ServletUriComponentsBuilder.fromCurrentContextPath().build().getPath());

        if (error != null) {
            request.getSession().invalidate();
            throw new CzertainlyAuthenticationException("Error during authentication: " + error);
        }

        AuthenticationSettingsDto authenticationSettings = SettingsCache.getSettings(SettingsSection.AUTHENTICATION);

        // Work only with properly configured OAuth2 providers.
        List<OAuth2ProviderSettingsDto> oauth2Providers = authenticationSettings.getOAuth2Providers() != null
                ? authenticationSettings.getOAuth2Providers().values().stream().filter(this::validOAuth2Provider).toList()
                : List.of();

        return oauth2Providers.stream()
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
    }

    @Override
    public void loginWithProvider(String provider, String redirect) {
        HttpServletRequest request = getHttpServletRequest();
        HttpServletResponse response = getHttpServletResponse();

        String baseUrl = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .replacePath(null)
                .build()
                .toUriString();

        String validatedRedirectUrl = validateAndNormalizeRedirect(redirect);
        if (validatedRedirectUrl != null) {
            request.getSession(true).setAttribute(OAuth2Constants.REDIRECT_URL_SESSION_ATTRIBUTE, baseUrl + validatedRedirectUrl);
        } else {
            String message = "Missing redirect URL. Please start the login from the beginning.";
            auditLogService.logAuthentication(Operation.LOGIN, OperationResult.FAILURE, message, null);
            throw new CzertainlyAuthenticationException(message);
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

        request.getSession().setAttribute(OAuth2Constants.SERVLET_CONTEXT_SESSION_ATTRIBUTE, ServletUriComponentsBuilder.fromCurrentContextPath().build().getPath());
        request.getSession().setMaxInactiveInterval(providerSettings.getSessionMaxInactiveInterval());

        String redirectUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().getPath() + "/oauth2/authorization/" + provider;
        try {
            response.sendRedirect(redirectUrl);
        } catch (IOException e) {
            log.error("Error occurred when sending redirect for provider {} to {} after authentication via OAuth2.", provider, redirectUrl, e);
        }
    }

    @Override
    public String getJwkSet(String provider) {
        AuthenticationSettingsDto authenticationSettings = SettingsCache.getSettings(SettingsSection.AUTHENTICATION);

        if (authenticationSettings.getOAuth2Providers() == null || authenticationSettings.getOAuth2Providers().get(provider) == null) {
            throw new ValidationException("Provider %s does not exist.".formatted(provider));
        }

        String jwkSetEncoded = authenticationSettings.getOAuth2Providers().get(provider).getJwkSet();
        if (jwkSetEncoded == null) {
            throw new ValidationException("Provider %s does not have JWK Set set up.".formatted(provider));
        }

        return new String(Base64.getDecoder().decode(jwkSetEncoded));
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

    private HttpServletRequest getHttpServletRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new IllegalStateException("No ServletRequestAttributes found in RequestContextHolder");
        }
        return attributes.getRequest();
    }

    private HttpServletResponse getHttpServletResponse() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new IllegalStateException("No ServletRequestAttributes found in RequestContextHolder");
        }
        return attributes.getResponse();
    }
}
