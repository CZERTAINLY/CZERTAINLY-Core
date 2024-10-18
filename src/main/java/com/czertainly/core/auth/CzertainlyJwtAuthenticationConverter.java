package com.czertainly.core.auth;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authn.client.AuthenticationInfo;
import com.czertainly.core.security.authn.client.CzertainlyAuthenticationClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
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
            if (claims.get("username") != null) extractedClaims.put("username", claims.get("username"));
            else if (claims.get("preferred_username") != null)
                extractedClaims.put("username", claims.get("preferred_username"));

            if (claims.get("given_name") != null) extractedClaims.put("given_name", claims.get("given_name"));

            if (claims.get("family_name") != null) extractedClaims.put("family_name", claims.get("family_name"));

            if (claims.get("email") != null) extractedClaims.put("email", claims.get("email"));

            if (claims.get("roles") != null) extractedClaims.put("roles", claims.get("roles"));
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
                throw new ValidationException("Unable to convert JWT token to authentication.");
            }
        } else {
            return (CzertainlyAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        }
    }

    private boolean isAuthenticationNeeded() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context == null) {
            return true;
        }
        Authentication auth = context.getAuthentication();

        if (auth == null) {
            return true;
        }

        return auth instanceof AnonymousAuthenticationToken;
    }
}
