package com.czertainly.core.auth.oauth2;

import com.czertainly.core.util.Constants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class CzertainlyOAuth2FailureHandler implements AuthenticationFailureHandler {

    private static final Logger logger = LoggerFactory.getLogger(CzertainlyOAuth2FailureHandler.class);

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
        logger.debug("Error occurred when trying to authenticate using OAuth2 protocol: {}", exception.getMessage());
        try {
            response.sendRedirect(request.getSession().getAttribute(Constants.SERVLET_CONTEXT_SESSION_ATTRIBUTE) + "/login?error=" + URLEncoder.encode(exception.getMessage(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Error when redirecting to error page: {}", e.getMessage());
        }
    }
}
