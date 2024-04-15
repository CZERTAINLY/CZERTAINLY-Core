package com.czertainly.core.config;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.core.util.AuthHelper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

@Component
public class ProtocolValidationFilter extends OncePerRequestFilter {

    private HandlerExceptionResolver resolver;

    private AuthHelper authHelper;

    @Autowired
    public void setAuthHelper(AuthHelper authHelper) {
        this.authHelper = authHelper;
    }

    @Autowired
    public void setHandlerExceptionResolver(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {

        CustomHttpServletRequestWrapper requestWrapper = new CustomHttpServletRequestWrapper(request);
        CustomHttpServletResponseWrapper responseWrapper = new CustomHttpServletResponseWrapper(response);
        String requestUri = request.getRequestURI();

        if (!requestUri.startsWith("/api/v1/protocols/")) {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } else if (requestUri.startsWith("/api/v1/protocols/scep/")) {
            logger.info("SCEP Request from " + request.getRemoteAddr() + " for " + requestUri);
            authHelper.authenticateAsSystemUser(AuthHelper.SCEP_USERNAME);
            filterChain.doFilter(requestWrapper, responseWrapper);
        } else if (requestUri.startsWith("/api/v1/protocols/acme/")) {
            logger.info("ACME Request from " + request.getRemoteAddr() + " for " + requestUri);
            authHelper.authenticateAsSystemUser(AuthHelper.ACME_USERNAME);
            filterChain.doFilter(requestWrapper, responseWrapper);
        } else if (requestUri.startsWith("/api/v1/protocols/cmp/")) {
            logger.info("CMPv2 Request from " + request.getRemoteAddr() + " for " + requestUri);
            authHelper.authenticateAsSystemUser(AuthHelper.CMP_USERNAME);
            filterChain.doFilter(requestWrapper, responseWrapper);
        } else {
            resolver.resolveException(request, response, null, new ValidationException("Invalid protocol request"));
        }

    }
}
