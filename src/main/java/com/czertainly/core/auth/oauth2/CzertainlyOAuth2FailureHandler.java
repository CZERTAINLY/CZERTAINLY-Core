package com.czertainly.core.auth.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class CzertainlyOAuth2FailureHandler implements AuthenticationFailureHandler {

    private static final Logger logger = LoggerFactory.getLogger(CzertainlyOAuth2FailureHandler.class);

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
        logger.debug("Error occurred when trying to authenticate using OAuth2 protocol: {}", exception.getMessage());
        try {
            response.sendRedirect(contextPath + "/login?error=" + URLEncoder.encode(exception.getMessage(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Error when redirecting to error page: {}", e.getMessage());
        }
    }
}
