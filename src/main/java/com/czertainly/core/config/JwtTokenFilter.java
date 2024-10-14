package com.czertainly.core.config;

import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authn.client.AuthenticationInfo;
import com.czertainly.core.security.authn.client.CzertainlyAuthenticationClient;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.text.ParseException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class JwtTokenFilter extends OncePerRequestFilter {

    @Value("${auth.token.header-name}")
    private String authTokenHeaderName;

    @Autowired
    private CzertainlyAuthenticationClient authenticationClient;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;

            OAuth2User oauthUser = oauthToken.getPrincipal();

            Map<String, Object> extractedClaims = new HashMap<>();
            extractedClaims.put("sub", oauthUser.getAttribute("sub"));
            extractedClaims.put("username", oauthUser.getAttribute("preferred_username"));
            extractedClaims.put("given_name", oauthUser.getAttribute("given_name"));
            extractedClaims.put("family_name", oauthUser.getAttribute("family_name"));
            extractedClaims.put("email", oauthUser.getAttribute("email"));
//            List<String> roles = List.of("superadmin");
            extractedClaims.put("roles", oauthUser.getAttribute("roles"));

            String encodedPayload = Base64.getEncoder().encodeToString(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(extractedClaims).getBytes());
            HttpHeaders headers = new HttpHeaders();
            headers.add(authTokenHeaderName, encodedPayload);
            AuthenticationInfo authInfo = authenticationClient.authenticate(headers);
            CzertainlyAuthenticationToken authenticationToken = new CzertainlyAuthenticationToken(new CzertainlyUserDetails(authInfo));
            authenticationToken.setIdToken(((OidcUser) authentication.getPrincipal()).getIdToken().getTokenValue());
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);

            // Perform any additional processing or validation here
        }

        // Proceed with the next filter
        filterChain.doFilter(request, response);
    }

}

