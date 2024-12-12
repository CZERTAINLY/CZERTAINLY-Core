package com.czertainly.core.auth.oauth2;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.*;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.core.logging.LoggingHelper;
import com.czertainly.core.service.AuditLogService;
import com.czertainly.core.util.OAuth2Constants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
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
        auditLogService.log(Module.AUTH, Resource.USER, Operation.AUTHENTICATION, OperationResult.FAILURE, message);
        logger.error(message);

        try {
            response.sendRedirect(request.getSession().getAttribute(OAuth2Constants.SERVLET_CONTEXT_SESSION_ATTRIBUTE) + "/login?error=" + URLEncoder.encode(exception.getMessage(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Error when redirecting to error page: {}", e.getMessage());
        }
    }
}
