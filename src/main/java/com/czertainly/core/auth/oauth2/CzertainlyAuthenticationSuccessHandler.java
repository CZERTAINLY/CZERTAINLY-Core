package com.czertainly.core.auth.oauth2;

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

    @Autowired
    public void setAuthorizedClientService(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {

        OAuth2AuthenticationToken authenticationToken = (OAuth2AuthenticationToken) authentication;

        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(authenticationToken.getAuthorizedClientRegistrationId(), authentication.getName());

        request.getSession().setAttribute("ACCESS_TOKEN", authorizedClient.getAccessToken());
        request.getSession().setAttribute("REFRESH_TOKEN", authorizedClient.getRefreshToken());

        String redirectUrl = (String) request.getSession().getAttribute("redirectUrl");
        request.getSession().removeAttribute("redirectUrl");

        try {
            response.sendRedirect(redirectUrl);
        } catch (IOException e) {
            logger.error("Error occurred when sending redirect to {} after authentication via OAuth2. ", redirectUrl);
            return;
        }
        logger.debug("Authentication via OAuth2 successful, redirecting to {}", redirectUrl);


    }
}
