package com.czertainly.core.security.authn;

import com.czertainly.core.util.AuthHelper;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CzertainlyAuthenticationConverter implements AuthenticationConverter {

    @Override
    public CzertainlyAuthenticationRequest convert(HttpServletRequest request) {
        HttpHeaders httpHeaders = extractHttpHeaders(request);

        boolean isLocalhostAddress = false;
        String ipAddress = AuthHelper.getClientIPAddress(request);
        try {
            InetAddress address = InetAddress.getByName(ipAddress);
            isLocalhostAddress = address.isAnyLocalAddress() || address.isLoopbackAddress();
        } catch (UnknownHostException ignored) {
        }

        return new CzertainlyAuthenticationRequest(httpHeaders, new WebAuthenticationDetails(request), isLocalhostAddress);
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
