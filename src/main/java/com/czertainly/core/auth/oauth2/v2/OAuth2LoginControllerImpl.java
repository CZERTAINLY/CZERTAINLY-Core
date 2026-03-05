package com.czertainly.core.auth.oauth2.v2;

import com.czertainly.api.interfaces.core.web.v2.OAuth2LoginController;
import com.czertainly.api.model.core.auth.LoginProviderDto;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.logging.enums.OperationResult;
import com.czertainly.api.model.core.settings.authentication.OAuth2ProviderSettingsDto;
import com.czertainly.core.security.authn.CzertainlyAuthenticationException;
import com.czertainly.core.service.AuditLogService;
import com.czertainly.core.service.v2.OAuth2LoginService;
import com.czertainly.core.util.OAuth2Constants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.util.List;

@Controller
@Slf4j
public class OAuth2LoginControllerImpl implements OAuth2LoginController {

    private AuditLogService auditLogService;
    private OAuth2LoginService oauth2LoginService;

    @Autowired
    public void setAuditLogService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Autowired
    public void setOauth2LoginService(OAuth2LoginService oauth2LoginService) {
        this.oauth2LoginService = oauth2LoginService;
    }

    @Override
    @ResponseBody
    public List<LoginProviderDto> getOAuth2Providers(String error) {
        HttpServletRequest request = getHttpServletRequest();
        request.getSession().setAttribute(OAuth2Constants.SERVLET_CONTEXT_SESSION_ATTRIBUTE, ServletUriComponentsBuilder.fromCurrentContextPath().build().getPath());

        if (error != null) {
            request.getSession().invalidate();
            throw new CzertainlyAuthenticationException("Error during authentication: " + error);
        }

        // Work only with properly configured OAuth2 providers.
        List<OAuth2ProviderSettingsDto> oauth2Providers = oauth2LoginService.getValidOAuth2Providers();

        return oauth2Providers.stream()
                .map(provider -> {
                    LoginProviderDto loginProvider = new LoginProviderDto();
                    loginProvider.setName(provider.getName());
                    String loginUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                            .path("/v2/oauth2/providers/{provider}/login")
                            .buildAndExpand(provider.getName())
                            .encode()
                            .toUriString();
                    loginProvider.setLoginUrl(loginUrl);
                    return loginProvider;
                })
                .toList();
    }

    @Override
    public ResponseEntity<Void> loginWithProvider(String provider, String redirect) {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .replacePath(null)
                .build()
                .toUriString();

        String validatedRedirectUrl = oauth2LoginService.validateAndNormalizeRedirect(redirect);
        if (validatedRedirectUrl == null) {
            String message = "Missing or invalid redirect URL. Please start the login from the beginning.";
            auditLogService.logAuthentication(Operation.LOGIN, OperationResult.FAILURE, message, null);
            throw new CzertainlyAuthenticationException(message);
        }

        HttpServletRequest request = getHttpServletRequest();
        HttpServletResponse response = getHttpServletResponse();
        request.getSession(true).setAttribute(OAuth2Constants.REDIRECT_URL_SESSION_ATTRIBUTE, baseUrl + validatedRedirectUrl);

        OAuth2ProviderSettingsDto providerSettings = oauth2LoginService.getOAuth2ProviderSettings(provider);

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

        String redirectUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().getPath() + "/v2/oauth2/providers/" + provider;
        try {
            response.sendRedirect(redirectUrl);
        } catch (IOException e) {
            String message = "Error occurred when sending redirect for provider '%s' to '%s' after authentication via OAuth2.".formatted(provider, redirectUrl);
            log.error(message, e);
            throw new CzertainlyAuthenticationException(message, e);
        }
        return null;
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
