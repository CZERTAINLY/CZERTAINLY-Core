package com.czertainly.core.config;

import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.util.AuthHelper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class CustomLogoutSuccessHandler extends OidcClientInitiatedLogoutSuccessHandler {


    public CustomLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository) {
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
