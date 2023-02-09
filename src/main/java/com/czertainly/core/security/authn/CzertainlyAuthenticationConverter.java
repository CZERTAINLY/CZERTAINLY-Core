package com.czertainly.core.security.authn;

import org.springframework.http.HttpHeaders;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CzertainlyAuthenticationConverter implements AuthenticationConverter {

    @Override
    public CzertainlyAuthenticationRequest convert(HttpServletRequest request) {
        HttpHeaders httpHeaders = extractHttpHeaders(request);
        return new CzertainlyAuthenticationRequest(httpHeaders, new WebAuthenticationDetails(request));
    }

    private HttpHeaders extractHttpHeaders(HttpServletRequest request) {
        return Collections.list(request.getHeaderNames())
                .stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        headerName -> Collections.list(request.getHeaders(headerName)),
                        (oldValue, newValue) -> newValue,
                        HttpHeaders::new
                ));
    }
}
