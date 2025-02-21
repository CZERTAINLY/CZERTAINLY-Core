package com.czertainly.core.util;

import com.czertainly.api.model.core.settings.authentication.OAuth2ProviderSettingsDto;
import com.czertainly.core.security.authn.CzertainlyAuthenticationException;
import com.nimbusds.jwt.SignedJWT;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OAuth2Util {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2Util.class);


    private OAuth2Util() {
        throw new IllegalStateException("Utility class");
    }

    public static void validateAudiences(OAuth2AccessToken accessToken, OAuth2ProviderSettingsDto providerSettings) {

        List<String> clientAudiences = providerSettings.getAudiences();
        List<String> tokenAudiences;
        try {
            tokenAudiences = SignedJWT.parse(accessToken.getTokenValue()).getJWTClaimsSet().getAudience();
        } catch (ParseException e) {
            throw new CzertainlyAuthenticationException("Could not parse JWT Access Token.");
        }

        if (!(clientAudiences == null || clientAudiences.isEmpty() || tokenAudiences != null && tokenAudiences.stream().anyMatch(clientAudiences::contains))) {
            String errorMessage = "User was not authenticated: audiences %s in access token issued by OAuth2 Provider do not match any of audiences %s set for the provider in settings. Token: %s".formatted(StringUtils.join(tokenAudiences), StringUtils.join(clientAudiences), accessToken.getTokenValue());
            throw new CzertainlyAuthenticationException(errorMessage);
        }

    }

    public static Map<String, Object> getUserInfo(String userInfoUrl, String accessToken) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        HttpMethod httpMethod = HttpMethod.GET;
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        URI uri = UriComponentsBuilder
                .fromUriString(userInfoUrl)
                .build()
                .toUri();

        RequestEntity<?> request;
        headers.setBearerAuth(accessToken);
        request = new RequestEntity<>(headers, httpMethod, uri);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(request, new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    private static Map<String, Object> mergeClaims(Map<String, Object> accessTokenClaims, Map<String,Object> idTokenClaims, Map<String, Object> userInfoClaims) {
        Map<String,Object> mergedClaims = new HashMap<>();

        if (accessTokenClaims != null) mergedClaims.putAll(accessTokenClaims);
        if (userInfoClaims != null) mergedClaims.putAll(userInfoClaims);
        if (idTokenClaims != null) mergedClaims.putAll(idTokenClaims);

        return mergedClaims;
    }

    public static Map<String, Object> getAllClaimsAvailable(OAuth2ProviderSettingsDto providerSettings, String accessTokenValue, OidcIdToken idToken) {
        Map<String, Object> userInfoClaims = null;
        if (providerSettings != null && providerSettings.getUserInfoUrl() != null) {
            try {
                userInfoClaims = getUserInfo(providerSettings.getUserInfoUrl(), accessTokenValue);
            } catch (Exception e) {
                logger.warn("Could not access User Info Endpoint {}: {}", providerSettings.getUserInfoUrl(), e.getMessage());
            }
        }

        Map<String, Object> accessTokenClaims;
        try {
            accessTokenClaims = SignedJWT.parse(accessTokenValue).getJWTClaimsSet().getClaims();
        } catch (ParseException e) {
            String message = "Could not convert access token to JWT and extract claims: %s".formatted(e.getMessage());
            throw new CzertainlyAuthenticationException(message);
        }

        Map<String, Object> claims = mergeClaims(accessTokenClaims, idToken == null ? null : idToken.getClaims(), userInfoClaims);
        if (!claims.containsKey(OAuth2Constants.TOKEN_USERNAME_CLAIM_NAME)) {
            String message = "The username claim could not be retrieved from the Access Token, User Info Endpoint, or ID Token claims for user authenticating with Access Token. Claims %s, Token: %s".formatted(StringUtils.join(claims), accessTokenValue);
            throw new CzertainlyAuthenticationException(message);
        }

        return claims;
    }

}
