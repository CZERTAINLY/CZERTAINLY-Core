package com.czertainly.core.config;

import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authn.client.AuthenticationInfo;
import com.czertainly.core.security.authn.client.CzertainlyAuthenticationClient;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    public void setAuthorizedClientService(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    @Value("${auth.token.header-name}")
    private String authTokenHeaderName;

    private OAuth2AuthorizedClientService authorizedClientService;

    private final HttpSessionRequestCache requestCache = new HttpSessionRequestCache();


    @Autowired
    private CzertainlyAuthenticationClient authenticationClient;


    private void createSessionForUser(HttpServletRequest request, HttpServletResponse response, String userName) {
        // Generate session and bind user info to session
        request.getSession().setAttribute("username", userName);

        // You can configure the session settings (timeout, HttpOnly, Secure, etc.)
        request.getSession().setMaxInactiveInterval(30 * 60); // Session timeout (30 minutes)

        // Add session cookie (if not already done by Spring)
        // Optionally customize session cookie security attributes here

    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        // Cast authentication to OAuth2AuthenticationToken
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = oauthToken.getPrincipal();
        String userName = oauthUser.getAttribute("name");



//


        // Retrieve the OAuth2AuthorizedClient, which contains the tokens
        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());

        if (authorizedClient != null) {
            // Access the access token
            OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
            String tokenValue = accessToken.getTokenValue();
            Instant expiresAt = accessToken.getExpiresAt();

            // Optionally access the refresh token if needed
            // OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();

            // Store token securely in session or backend
            request.getSession().setAttribute("oauth2AccessToken", tokenValue);

            // (Optional) Store the token in the database or an in-memory store
//             saveTokenInDb(userName, tokenValue, expiresAt);

            // Log the access token (for debugging purposes)
            System.out.println("Access Token: " + tokenValue);
            System.out.println("Expires At: " + expiresAt);
        }

        // Create session cookie for the user
        createSessionForUser(request, response, userName);

        // Redirect to a post-login page (e.g., the home page)

        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null) {
            // Redirect to the original URL that triggered the login
            response.sendRedirect(savedRequest.getRedirectUrl());
        } else {
            // If no saved request, redirect to a default page (optional fallback)
            response.sendRedirect("http://localhost:3000/#/dashboard");
        }
    }
}

