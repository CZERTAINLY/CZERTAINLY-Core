package com.czertainly.core.auth.oauth2;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authn.client.AuthenticationInfo;
import com.czertainly.core.security.authn.client.CzertainlyAuthenticationClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;

public class CzertainlyJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Autowired
    private CzertainlyAuthenticationClient authenticationClient;

    @Value("${auth.token.header-name}")
    private String authTokenHeaderName;

    @Override
    public AbstractAuthenticationToken convert(Jwt source) {
        if (isAuthenticationNeeded()) {
            Map<String, Object> claims = source.getClaims();
            Map<String, Object> extractedClaims = new HashMap<>();

            extractedClaims.put("sub", claims.get("sub"));
            extractedClaims.put("username", claims.get("username"));
            extractedClaims.put("given_name", claims.get("given_name"));
            extractedClaims.put("family_name", claims.get("family_name"));
            extractedClaims.put("email", claims.get("email"));
            extractedClaims.put("roles", claims.get("roles") == null ? new ArrayList<>() : claims.get("roles"));
            try {
                String encodedPayload = Base64.getEncoder().encodeToString(new ObjectMapper().writeValueAsString(extractedClaims).getBytes());
                HttpHeaders headers = new HttpHeaders();
                headers.add(authTokenHeaderName, encodedPayload);
                AuthenticationInfo authInfo = authenticationClient.authenticate(headers);
                if (authInfo.isAnonymous()) {
                    return new AnonymousAuthenticationToken(UUID.randomUUID().toString(), new CzertainlyUserDetails(authInfo), authInfo.getAuthorities());
                }
                return new CzertainlyAuthenticationToken(new CzertainlyUserDetails(authInfo));

            } catch (JsonProcessingException e) {
                throw new ValidationException("Unable to convert JWT token to authentication.");
            }
        } else {
            return (CzertainlyAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        }
    }

    private boolean isAuthenticationNeeded() {
        SecurityContext context = SecurityContextHolder.getContext();
        return context == null || context.getAuthentication() == null || context.getAuthentication() instanceof AnonymousAuthenticationToken;
    }
}
