package com.czertainly.core.util;

import com.czertainly.api.model.core.settings.authentication.OAuth2ProviderSettingsDto;
import com.czertainly.core.security.authn.CzertainlyAuthenticationException;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OAuth2Util {

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
            String errorMessage = "User was not authenticated: audiences in access token issued by OAuth2 Provider do not match any of audiences set for the provider in settings.";
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

    public static Map<String, Object> mergeClaims(Map<String, Object> accessTokenClaims, Map<String,Object> idTokenClaims, Map<String, Object> userInfoClaims) {
        Map<String,Object> mergedClaims = new HashMap<>();

        if (accessTokenClaims != null) mergedClaims.putAll(accessTokenClaims);
        if (userInfoClaims != null) mergedClaims.putAll(userInfoClaims);
        if (idTokenClaims != null) mergedClaims.putAll(idTokenClaims);

        return mergedClaims;
    }

}
