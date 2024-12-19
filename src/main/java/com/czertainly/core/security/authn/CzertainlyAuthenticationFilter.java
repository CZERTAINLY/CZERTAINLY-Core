package com.czertainly.core.security.authn;

import com.czertainly.api.model.core.logging.enums.AuthMethod;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class CzertainlyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(CzertainlyAuthenticationFilter.class);

    private final AuthenticationManager authenticationManager;

    private final CzertainlyAuthenticationConverter authenticationConverter;

    private final String healthCheckRequest;

    private final String certificateHeaderName;


    public CzertainlyAuthenticationFilter(final AuthenticationManager authenticationManager, final CzertainlyAuthenticationConverter authenticationConverter, final String restApiPrefix, final String certificateHeaderName) {
        this.authenticationManager = authenticationManager;
        this.authenticationConverter = authenticationConverter;
        this.healthCheckRequest = "/api" + restApiPrefix + "health";
        this.certificateHeaderName = certificateHeaderName;
    }

    @Override
    public void afterPropertiesSet() {
        Assert.notNull(this.authenticationManager, "An AuthenticationManager is required");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (isAuthenticationNeeded(request)) {
            logger.trace("Going to authenticate the '{}' request on '{}'.", request.getMethod(), request.getRequestURI());
            CzertainlyAuthenticationRequest authRequest = this.authenticationConverter.convert(request);

            try {
                Authentication authResult = this.authenticationManager.authenticate(authRequest);
                logger.trace("Authentication result: {}", authResult.isAuthenticated() ? "authenticated" : "unauthenticated");

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authResult);
                SecurityContextHolder.setContext(context);
                CzertainlyUserDetails userDetails = (CzertainlyUserDetails) authResult.getPrincipal();
                if (userDetails.getAuthMethod() == AuthMethod.CERTIFICATE) {
                    logger.debug("User with username '{}' has been successfully authenticated with certificate.", userDetails.getUsername());
                } else {
                    logger.debug("User has not been identified, using anonymous user.");
                }

            } catch (AuthenticationException e) {
                SecurityContextHolder.clearContext();
                if (logger.isDebugEnabled()) {
                    throw e;
                }
                logger.info("Authentication request for '{}' failed: {}", request.getRequestURI(), e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAuthenticationNeeded(final HttpServletRequest request) {

        if (request.getRequestURI().startsWith(healthCheckRequest)) {
            logger.trace("Actuator health checks are automatically authenticated. Will skip authentication.");
            return false;
        }

        // User is already authenticated and is not anonymous user
        SecurityContext context = SecurityContextHolder.getContext();
        if (context != null && context.getAuthentication() != null && context.getAuthentication().isAuthenticated()) {
            logger.trace("The user {} is already authenticated. Will not re-authenticate.", context.getAuthentication().getName());
            return false;
        }

        // If there is no token header, user will need to be authenticated in this filter
        return request.getHeader("Authorization") == null || request.getHeader(certificateHeaderName) != null;

    }

}

