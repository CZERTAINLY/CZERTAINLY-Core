package com.czertainly.core.auth.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.io.IOException;

public class CzertainlyLogoutSuccessHandler extends OidcClientInitiatedLogoutSuccessHandler {


    public CzertainlyLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository) {
        super(clientRegistrationRepository);
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException, ServletException {
        // Get the frontend redirect URI from the request parameter
        String redirectUri = request.getHeader("referer");

        // If the parameter is not present, use a default URI
        if (redirectUri == null || redirectUri.isEmpty()) {
            redirectUri = "/";
        }

        // Set the post logout redirect URI dynamically
        this.setPostLogoutRedirectUri(redirectUri);

        // Call the parent method to complete the logout process
        super.onLogoutSuccess(request, response, authentication);
    }

}
