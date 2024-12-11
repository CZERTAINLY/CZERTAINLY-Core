package com.czertainly.core.auth.oauth2;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.logging.enums.OperationResult;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.api.model.core.settings.authentication.AuthenticationSettingsDto;
import com.czertainly.api.model.core.settings.authentication.OAuth2ProviderSettingsDto;
import com.czertainly.core.security.authn.CzertainlyAuthenticationException;
import com.czertainly.core.service.AuditLogService;
import com.czertainly.core.settings.SettingsCache;
import com.czertainly.core.util.Constants;
import com.czertainly.core.util.OAuth2Util;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CzertainlyAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(CzertainlyAuthenticationSuccessHandler.class);

    private OAuth2AuthorizedClientService authorizedClientService;

    private AuditLogService auditLogService;

    @Autowired
    public void setAuditLogService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Autowired
    public void setAuthorizedClientService(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {

        OAuth2AuthenticationToken authenticationToken = (OAuth2AuthenticationToken) authentication;

        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(authenticationToken.getAuthorizedClientRegistrationId(), authentication.getName());
        AuthenticationSettingsDto authenticationSettings = SettingsCache.getSettings(SettingsSection.AUTHENTICATION);
        OAuth2ProviderSettingsDto providerSettings = authenticationSettings.getOAuth2Providers().get(authenticationToken.getAuthorizedClientRegistrationId());
        if (providerSettings == null) {
            request.getSession().invalidate();
            String message = "Unknown OAuth2 Provider with name '%s' for authentication with OAuth2 flow".formatted(authenticationToken.getAuthorizedClientRegistrationId());
            auditLogService.log(Module.AUTH, Resource.USER, Operation.AUTHENTICATION, OperationResult.FAILURE, message);
            throw new CzertainlyAuthenticationException(message);
        }
        try {
            OAuth2Util.validateAudiences(authorizedClient.getAccessToken(), providerSettings);
        } catch (CzertainlyAuthenticationException e) {
            request.getSession().invalidate();
            auditLogService.log(Module.AUTH, Resource.USER, Operation.AUTHENTICATION, OperationResult.FAILURE, e.getMessage());
            throw e;
        }

        request.getSession().setAttribute(Constants.ACCESS_TOKEN_SESSION_ATTRIBUTE, authorizedClient.getAccessToken());
        request.getSession().setAttribute(Constants.REFRESH_TOKEN_SESSION_ATTRIBUTE, authorizedClient.getRefreshToken());

        String redirectUrl = (String) request.getSession().getAttribute(Constants.REDIRECT_URL_SESSION_ATTRIBUTE);
        request.getSession().removeAttribute(Constants.REDIRECT_URL_SESSION_ATTRIBUTE);
        request.getSession().removeAttribute(Constants.SERVLET_CONTEXT_SESSION_ATTRIBUTE);

        try {
            response.sendRedirect(redirectUrl);
        } catch (IOException e) {
            logger.error("Error occurred when sending redirect to {} after authentication via OAuth2. ", redirectUrl);
            return;
        }
        logger.debug("Authentication of user {} via OAuth2 successful, redirecting to {}", authenticationToken.getPrincipal().getAttribute("username"), redirectUrl);


    }
}
