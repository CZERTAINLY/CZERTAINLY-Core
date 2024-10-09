package com.czertainly.core.config;

import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.util.AuthHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class CustomLogoutSuccessHandler implements LogoutSuccessHandler {


    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        // Redirect to OAuth2 provider's logout URL


//        String redirectUri = URLEncoder.encode("http://localhost:3000", StandardCharsets.UTF_8.toString());

        String logoutUrl = "http://localhost:8080/realms/CZERTAINLY-realm/protocol/openid-connect/logout?post_logout_redirect_uri=http://localhost:3000/&id_token_hint=" +
                ((OidcUser)authentication.getPrincipal()).getIdToken().getTokenValue(); // Update with your provider's URL and redirect URI

        response.sendRedirect(logoutUrl); // Redirect to OAuth2 provider's logout
    }

}
