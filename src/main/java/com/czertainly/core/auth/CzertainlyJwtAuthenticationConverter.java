package com.czertainly.core.auth;

import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authn.client.AuthenticationInfo;
import com.czertainly.core.security.authn.client.CzertainlyAuthenticationClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CzertainlyJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Autowired
    private CzertainlyAuthenticationClient authenticationClient;

    @Value("${auth.token.header-name}")
    private String authTokenHeaderName;
    @Override
    public AbstractAuthenticationToken convert(Jwt source) {
        Map<String, Object> claims = source.getClaims();
        Map<String, Object> extractedClaims = new HashMap<>();
        extractedClaims.put("sub", claims.get("sub"));
        extractedClaims.put("username", claims.get("username") == null ? claims.get("preferred_username") : claims.get("username"));
        extractedClaims.put("given_name", claims.get("given_name"));
        extractedClaims.put("family_name", claims.get("family_name"));
        extractedClaims.put("email", claims.get("email"));
        extractedClaims.put("roles", claims.get("roles"));
        try {
            String encodedPayload = Base64.getEncoder().encodeToString(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(extractedClaims).getBytes());
            HttpHeaders headers = new HttpHeaders();
            headers.add(authTokenHeaderName, encodedPayload);
            AuthenticationInfo authInfo = authenticationClient.authenticate(headers);
            if (authInfo.isAnonymous()) {
                return new AnonymousAuthenticationToken(UUID.randomUUID().toString(), new CzertainlyUserDetails(authInfo), authInfo.getAuthorities());
            }
            return new CzertainlyAuthenticationToken(new CzertainlyUserDetails(authInfo));

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
