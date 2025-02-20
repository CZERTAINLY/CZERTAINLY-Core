package com.czertainly.core.security.authn;

import com.czertainly.api.model.core.logging.enums.AuthMethod;
import com.czertainly.core.security.authn.client.AuthenticationInfo;
import com.czertainly.core.security.authn.client.CzertainlyAuthenticationClient;
import com.czertainly.core.util.AuthHelper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

public class CzertainlyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CzertainlyAuthenticationFilter.class);

    private final String healthCheckRequest;

    private final String certificateHeaderName;
    private final CzertainlyAuthenticationClient authClient;


    public CzertainlyAuthenticationFilter(CzertainlyAuthenticationClient authClient, final String restApiPrefix, final String certificateHeaderName) {
        this.authClient = authClient;
        this.healthCheckRequest = "/api" + restApiPrefix + "health";
        this.certificateHeaderName = certificateHeaderName;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (isAuthenticationNeeded(request)) {
            log.trace("Going to authenticate the '{}' request on '{}'.", request.getMethod(), request.getRequestURI());

            try {

                logger.trace("Going to authenticate users against the Czertainly Authentication Service.");

                AuthMethod authMethod = AuthMethod.NONE;
                Object authData = null;

                if (request.getHeader(certificateHeaderName) != null) {
                    authMethod = AuthMethod.CERTIFICATE;
                    authData = request.getHeader(certificateHeaderName);
                }

                AuthenticationInfo authInfo = authClient.authenticate(authMethod, authData, isLocalhostAddress(request));

                Authentication authentication;
                if (authInfo.isAnonymous()) {
                    authentication = new AnonymousAuthenticationToken(UUID.randomUUID().toString(), new CzertainlyUserDetails(authInfo), authInfo.getAuthorities());
                } else {
                    authentication = new CzertainlyAuthenticationToken(new CzertainlyUserDetails(authInfo));
                }

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
                CzertainlyUserDetails userDetails = (CzertainlyUserDetails) authentication.getPrincipal();
                if (userDetails.getAuthMethod() == AuthMethod.CERTIFICATE) {
                    log.debug("User with username '{}' has been successfully authenticated with certificate.", userDetails.getUsername());
                } else {
                    log.debug("User has not been identified, using anonymous user.");
                }

            } catch (AuthenticationException e) {
                SecurityContextHolder.clearContext();
                if (e instanceof CzertainlyAuthenticationException) {
                    log.warn("Authentication request for '{}' failed: {}", request.getRequestURI(), e.getMessage());
                } else {
                    throw e;
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAuthenticationNeeded(final HttpServletRequest request) {

        if (request.getRequestURI().startsWith(healthCheckRequest)) {
            log.trace("Actuator health checks are automatically authenticated. Will skip authentication.");
            return false;
        }

        // User is already authenticated and is not anonymous user
        SecurityContext context = SecurityContextHolder.getContext();
        if (context != null && context.getAuthentication() != null && context.getAuthentication().isAuthenticated()) {
            log.trace("The user {} is already authenticated. Will not re-authenticate.", context.getAuthentication().getName());
            return false;
        }

        // If there is no token header, user will need to be authenticated in this filter
        return request.getHeader("Authorization") == null || request.getHeader(certificateHeaderName) != null;

    }

    private boolean isLocalhostAddress(HttpServletRequest request){
        boolean isLocalhostAddress;
        String ipAddress = AuthHelper.getClientIPAddress(request);
        try {
            InetAddress address = InetAddress.getByName(ipAddress);
            isLocalhostAddress = address.isAnyLocalAddress() || address.isLoopbackAddress();
        } catch (UnknownHostException ignored) {
            isLocalhostAddress = false;
        }
        return isLocalhostAddress;
    }

}

