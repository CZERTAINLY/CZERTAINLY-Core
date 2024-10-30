package com.czertainly.core.auth.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.io.IOException;

public class CzertainlyLogoutSuccessHandler extends OidcClientInitiatedLogoutSuccessHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CzertainlyLogoutSuccessHandler.class);


    private CzertainlyClientRegistrationRepository clientRegistrationRepository;

    public CzertainlyLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository) {
        super(clientRegistrationRepository);
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {

        String redirectUri = clientRegistrationRepository.findByRegistrationId(((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId()).getProviderDetails().getConfigurationMetadata().get("post_logout_uri").toString();

        // Set the post logout redirect URI dynamically
        this.setPostLogoutRedirectUri(redirectUri);

        // Call the parent method to complete the logout process
        try {
            super.onLogoutSuccess(request, response, authentication);
        } catch (IOException | ServletException e) {
            LOGGER.error("Error occurred when logging out from OAuth2 Provider: {}", e.getMessage());
        }

        LOGGER.debug("Logout from OAuth2 Provider successful, redirecting to {}", redirectUri);
    }

    @Autowired
    public void setClientRegistrationRepository(CzertainlyClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }


}
