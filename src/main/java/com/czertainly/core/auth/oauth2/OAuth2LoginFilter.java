package com.czertainly.core.auth.oauth2;

import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.api.model.core.settings.authentication.AuthenticationSettingsDto;
import com.czertainly.api.model.core.settings.authentication.OAuth2ProviderSettingsDto;
import com.czertainly.core.security.authn.CzertainlyAuthenticationException;
import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authn.client.AuthenticationInfo;
import com.czertainly.core.security.authn.client.CzertainlyAuthenticationClient;
import com.czertainly.core.settings.SettingsCache;
import com.czertainly.core.util.Constants;
import com.czertainly.core.util.OAuth2Util;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class OAuth2LoginFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuth2LoginFilter.class);

    private CzertainlyAuthenticationClient authenticationClient;
    private CzertainlyClientRegistrationRepository clientRegistrationRepository;

    private OAuth2AuthorizedClientProvider authorizedClientProvider;

    @Autowired
    public void setAuthorizedClientProvider(OAuth2AuthorizedClientProvider authorizedClientProvider) {
        this.authorizedClientProvider = authorizedClientProvider;
    }

    @Autowired
    public void setAuthenticationClient(CzertainlyAuthenticationClient authenticationClient) {
        this.authenticationClient = authenticationClient;
    }

    @Autowired
    public void setClientRegistrationRepository(CzertainlyClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {

            AuthenticationSettingsDto authenticationSettings = SettingsCache.getSettings(SettingsSection.AUTHENTICATION);
            OAuth2ProviderSettingsDto providerSettings = authenticationSettings.getOAuth2Providers().get(oauthToken.getAuthorizedClientRegistrationId());
            if (providerSettings == null) {
                request.getSession().invalidate();
                throw new CzertainlyAuthenticationException("Unknown OAuth2 Provider with name '%s'".formatted(oauthToken.getAuthorizedClientRegistrationId()));
            }

            ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId(oauthToken.getAuthorizedClientRegistrationId());
            OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(clientRegistration, oauthToken.getName(), (OAuth2AccessToken) request.getSession().getAttribute(Constants.ACCESS_TOKEN_SESSION_ATTRIBUTE), (OAuth2RefreshToken) request.getSession().getAttribute(Constants.REFRESH_TOKEN_SESSION_ATTRIBUTE));

            Instant now = Instant.now();
            Instant expiresAt = authorizedClient.getAccessToken().getExpiresAt();
            int skew = providerSettings.getSkew();

            // If the access token is expired, try to refresh it
            if (expiresAt != null && expiresAt.isBefore(now.plus(skew, ChronoUnit.SECONDS))) {
                try {
                    refreshToken(oauthToken, authorizedClient, request.getSession(), clientRegistration);
                } catch (ClientAuthorizationException | CzertainlyAuthenticationException e) {
                    request.getSession().invalidate();
                    throw new CzertainlyAuthenticationException("Could not refresh token: " + e.getMessage());
                }
                try {
                    OAuth2Util.validateAudiences(authorizedClient.getAccessToken(), providerSettings);
                } catch (CzertainlyAuthenticationException e) {
                    request.getSession().invalidate();
                    throw e;
                }
            }

            HttpHeaders headers = new HttpHeaders();
            headers.add(Constants.TOKEN_AUTHENTICATION_HEADER, authorizedClient.getAccessToken().getTokenValue());
            AuthenticationInfo authInfo = authenticationClient.authenticate(headers, false, true);
            CzertainlyAuthenticationToken authenticationToken = new CzertainlyAuthenticationToken(new CzertainlyUserDetails(authInfo));
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            LOGGER.debug("OAuth2 Authentication Token has been converted to Czertainly Authentication Token with username {}.", authenticationToken.getPrincipal().getUsername());
        }

        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException e) {
            request.getSession().invalidate();
            LOGGER.error("Error when proceeding with OAuth2Login filter: {}", e.getMessage());
        }
    }

    private void refreshToken(OAuth2AuthenticationToken oauthToken, OAuth2AuthorizedClient authorizedClient, HttpSession session, ClientRegistration clientRegistration) {

        if (authorizedClient.getRefreshToken() != null) {
            // Refresh the token
            OAuth2AuthorizationContext context = OAuth2AuthorizationContext.withAuthorizedClient(authorizedClient)
                    .principal(oauthToken)
                    .attribute(OAuth2AuthorizationContext.REQUEST_SCOPE_ATTRIBUTE_NAME, clientRegistration.getScopes().toArray(new String[0]))
                    .build();

            authorizedClient = authorizedClientProvider.authorize(context);

            // Save the refreshed authorized client with refreshed access token
            if (authorizedClient != null) {
                LOGGER.debug("OAuth2 Access Token has been refreshed for user {}.", oauthToken.getPrincipal().getAttribute("username").toString());
                session.setAttribute(Constants.ACCESS_TOKEN_SESSION_ATTRIBUTE, authorizedClient.getAccessToken());
                session.setAttribute(Constants.REFRESH_TOKEN_SESSION_ATTRIBUTE, authorizedClient.getRefreshToken());
            } else {
                throw new CzertainlyAuthenticationException("Did not manage to refresh the access token.");
            }
        } else {
            throw new CzertainlyAuthenticationException("Cannot refresh access token, refresh token is not available.");
        }
    }

}

