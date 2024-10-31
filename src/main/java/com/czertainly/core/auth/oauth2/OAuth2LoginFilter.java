package com.czertainly.core.auth.oauth2;

import com.czertainly.core.security.authn.CzertainlyAuthenticationException;
import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authn.client.AuthenticationInfo;
import com.czertainly.core.security.authn.client.CzertainlyAuthenticationClient;
import com.czertainly.core.service.SettingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

@Component
public class OAuth2LoginFilter extends OncePerRequestFilter {

    @Value("${auth.token.header-name}")
    private String authTokenHeaderName;

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuth2LoginFilter.class);

    private CzertainlyAuthenticationClient authenticationClient;

    private SettingService settingService;

    @Autowired
    public void setSettingService(SettingService settingService) {
        this.settingService = settingService;
    }

    @Autowired
    public void setAuthenticationClient(CzertainlyAuthenticationClient authenticationClient) {
        this.authenticationClient = authenticationClient;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            LOGGER.debug("Converting OAuth2 Authentication Token to Czertainly Authentication Token.");

            OAuth2User oauthUser = oauthToken.getPrincipal();

            List<String> tokenAudiences = oauthUser.getAttribute("aud");
            List<String> clientAudiences = settingService.getOAuth2ProviderSettings(oauthToken.getAuthorizedClientRegistrationId(), false).getAudiences();

            if (!(clientAudiences == null || tokenAudiences != null && tokenAudiences.stream().anyMatch(clientAudiences::contains))) {
                throw new CzertainlyAuthenticationException("Audiences in access token issued by OAuth2 Provider do not match any of audiences set for the provider in settings.");
            }

            Map<String, Object> extractedClaims = new HashMap<>();
            extractedClaims.put("sub", oauthUser.getAttribute("sub"));
            extractedClaims.put("username", oauthUser.getAttribute("username"));
            extractedClaims.put("given_name", oauthUser.getAttribute("given_name"));
            extractedClaims.put("family_name", oauthUser.getAttribute("family_name"));
            extractedClaims.put("email", oauthUser.getAttribute("email"));
            extractedClaims.put("roles", oauthUser.getAttribute("roles") == null ? new ArrayList<>() : oauthUser.getAttribute("roles"));

            String encodedPayload = null;
            try {
                encodedPayload = Base64.getEncoder().encodeToString(new ObjectMapper().writeValueAsString(extractedClaims).getBytes());
            } catch (JsonProcessingException e) {
                LOGGER.error("Error when encoding JWT claims to payload: {}", e.getMessage());
            }
            HttpHeaders headers = new HttpHeaders();
            headers.add(authTokenHeaderName, encodedPayload);
            AuthenticationInfo authInfo = authenticationClient.authenticate(headers);
            CzertainlyAuthenticationToken authenticationToken = new CzertainlyAuthenticationToken(new CzertainlyUserDetails(authInfo));
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            LOGGER.debug("OAuth2 Authentication Token has been converted to Czertainly Authentication Token with username {}.", authenticationToken.getName());
        }

        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException e) {
            LOGGER.error("Error when proceeding with OAuth2Login filter: {}", e.getMessage());
        }
    }

}

