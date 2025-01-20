package com.czertainly.core.auth.oauth2;

import com.czertainly.api.model.core.logging.enums.*;
import com.czertainly.core.logging.LoggingHelper;
import com.czertainly.core.security.authn.CzertainlyAuthenticationException;
import com.czertainly.core.service.AuditLogService;
import com.czertainly.core.util.OAuth2Constants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class CzertainlyOAuth2FailureHandler implements AuthenticationFailureHandler {

    private static final Logger logger = LoggerFactory.getLogger(CzertainlyOAuth2FailureHandler.class);

    private AuditLogService auditLogService;

    @Autowired
    public void setAuditLogService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
        LoggingHelper.putActorInfoWhenNull(ActorType.USER, AuthMethod.SESSION);
        String message = "Error occurred when trying to authenticate using OAuth2 protocol: %s".formatted(exception.getMessage());
        if (!(exception instanceof CzertainlyAuthenticationException)) {
            String accessToken;
            try {
                OAuth2AccessToken oauth2AccessToken = (OAuth2AccessToken) request.getSession().getAttribute(OAuth2Constants.ACCESS_TOKEN_SESSION_ATTRIBUTE);
                accessToken = oauth2AccessToken.getTokenValue();
            } catch (Exception e) {
                accessToken = null;
            }
            auditLogService.logAuthentication(Operation.AUTHENTICATION, OperationResult.FAILURE, message, accessToken);
        }
        Object contextPath = request.getSession().getAttribute(OAuth2Constants.SERVLET_CONTEXT_SESSION_ATTRIBUTE);
        request.getSession().invalidate();
        logger.error(message);

        try {
            response.sendRedirect(contextPath + "/login?error=" + URLEncoder.encode(exception.getMessage(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Error when redirecting to error page: {}", e.getMessage());
        }
    }
}
