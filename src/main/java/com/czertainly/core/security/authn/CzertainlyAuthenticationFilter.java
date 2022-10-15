package com.czertainly.core.security.authn;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class CzertainlyAuthenticationFilter extends OncePerRequestFilter {

    protected final Log logger = LogFactory.getLog(this.getClass());

    private final AuthenticationManager authenticationManager;

    private final CzertainlyAuthenticationConverter authenticationConverter;

    public CzertainlyAuthenticationFilter(AuthenticationManager authenticationManager, CzertainlyAuthenticationConverter authenticationConverter) {
        this.authenticationManager = authenticationManager;
        this.authenticationConverter = authenticationConverter;
    }

    @Override
    public void afterPropertiesSet() {
        Assert.notNull(this.authenticationManager, "An AuthenticationManager is required");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (isAuthenticationNeeded()) {
            logger.trace(String.format("Going to authenticate the '%s' request on '%s'.", request.getMethod(), request.getRequestURI()));
            CzertainlyAuthenticationRequest authRequest = this.authenticationConverter.convert(request);

            try {
                Authentication authResult = this.authenticationManager.authenticate(authRequest);

                logger.trace(String.format("Authentication result: %s", authResult.isAuthenticated() ? "authenticated" : "unauthenticated"));

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authResult);
                SecurityContextHolder.setContext(context);
            } catch (AuthenticationException e) {
                logger.info("Failed to authenticate user.");
                logger.debug("Failed to authenticate user.", e);
                SecurityContextHolder.clearContext();
            }
        } else {
            logger.trace("The user is already authenticated. Will not re-authenticate");
        }

        filterChain.doFilter(request, response);
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

        return !auth.isAuthenticated();
    }
}

