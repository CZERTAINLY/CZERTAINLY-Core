package com.czertainly.core.security.authn.client;

import com.czertainly.core.security.authn.CzertainlyAuthenticationException;
import com.czertainly.core.security.authn.client.dto.AuthenticationResponseDto;
import com.czertainly.core.security.authn.client.dto.UserDetailsDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class CzertainlyAuthenticationClient {

    protected final Log logger = LogFactory.getLog(this.getClass());

    private static List<String> excludedHeaders = List.of("host", "content-length", "content-type", "accept", "accept-encoding", "connection");

    private WebClient client;


    private final ObjectMapper objectMapper;

    private final String authServiceBaseUrl;

    public CzertainlyAuthenticationClient(@Autowired ObjectMapper objectMapper, @Value("${auth-service.base-url}") String authServiceBaseUrl) {
        this.objectMapper = objectMapper;
        this.authServiceBaseUrl = authServiceBaseUrl;
    }

    public AuthenticationInfo authenticate(HttpHeaders headers) throws AuthenticationException {
        try {
            logger.trace(
                    String.format(
                            "Calling authentication service with the following headers [%s].",
                            headers.entrySet().stream()
                                    .map(e -> String.format("%s='%s'", e.getKey(), String.join(", ", e.getValue())))
                                    .collect(Collectors.joining(","))
                    )
            );
            WebClient.RequestHeadersSpec<?> request = getClient()
                    .get()
                    .uri("/auth/users/profile")
                    .accept(MediaType.APPLICATION_JSON);

            insertHeaders(headers, request);

            AuthenticationResponseDto response = request
                    .retrieve()
                    .bodyToMono(AuthenticationResponseDto.class)
                    .block();

            if (response == null) {
                throw new CzertainlyAuthenticationException("Empty response received from authentication service.");
            }
            return createAuthenticationInfo(response);
        } catch (WebClientResponseException.InternalServerError e) {
            throw new CzertainlyAuthenticationException("An error occurred when calling authentication service.", e);
        }
    }

    private AuthenticationInfo createAuthenticationInfo(AuthenticationResponseDto response) {
        if (!response.isAuthenticated()) {
            throw new CzertainlyAuthenticationException("The user has not been authenticated by the authentication service.");
        }

        try {
            UserDetailsDto userDetails = objectMapper.readValue(response.getData(), UserDetailsDto.class);
            return new AuthenticationInfo(
                    userDetails.getUser().getUsername(),
                    userDetails.getRoles().stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList()),
                    response.getData()
            );
        } catch (JsonProcessingException e) {
            throw new CzertainlyAuthenticationException("The response from the authentication service could not be parsed.", e);
        }
    }

    private void insertHeaders(HttpHeaders headers, WebClient.RequestHeadersSpec<?> request) {
        headers.forEach((name, value) -> {
            if (!excludedHeaders.contains(name)) {
                request.header(name, String.join(",", value));
            }
        });
        request.header("X-APP-CERTIFICATE", "-----BEGIN%20CERTIFICATE-----%0AMIIDPTCCAiUCFBd%2BdfQuley5j4MetX3iewvIxHZDMA0GCSqGSIb3DQEBCwUAMF0x%0ACzAJBgNVBAYTAkNaMRAwDgYDVQQIDAdDemVjaGlhMQswCQYDVQQHDAJDQjENMAsG%0AA1UECgwEM0tFWTEMMAoGA1UECwwDREVWMRIwEAYDVQQDDAlsb2NhbGhvc3QwHhcN%0AMjAwOTI1MTE1NDU3WhcNMzAwODA0MTE1NDU3WjBZMQswCQYDVQQGEwJDWjEQMA4G%0AA1UECAwHQ3plY2hpYTELMAkGA1UEBwwCQ0IxCzAJBgNVBAoMAkNGMQwwCgYDVQQL%0ADANERVYxEDAOBgNVBAMMB0NMSUVOVDEwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAw%0AggEKAoIBAQC%2FSsO%2B9IzQ85xxyiT%2Bou8RDNxZMP0Ja8YKrdu19BTFjyLtVLpb%2BI1X%0AqzlXFdJcObYZ5ZboyALB00i5Ds0TTs8ydgEeaw0K2O96DnGh4z5r4qLuF%2BfpVR%2B3%0AA8kKRSrqJN1JNPFeb%2BNKsilUNvx5plZBm5%2BVTd64Sop6r1DALEDBS8AxRJSgp4x%2F%0AoCq%2BT4zLh9XDyVUQ68axLgF86sS4YcBYKQVTH7KwRx%2BFGPFnBqt2ll2IherJ1N1d%0AheXdLqzPYY%2BuIhs55wUPRhQibjiJhM9NgMYsmOPZRzsPIr6%2BgUil82rmSfyMg%2FA0%0AwT4dsm6MT7ly6PPRyxoRvhNvfn96FsCRAgMBAAEwDQYJKoZIhvcNAQELBQADggEB%0AAI%2BYNR82n23p9014wa%2B99aEWJfujlirY07jhAQmsGTkkFM5QTNJzwi6VYnUwjlJM%0AOXw8fEiBVRHUiyLV5RWZGiGZuLdCZgYCjtzCtWuOPidShAK5GpLDipG9upZ%2BRCNp%0ABXVbb6J5tEI0esTSxZ%2Fjwj2JqZZayhRmRXL%2Fj8vGRn74atTILeFwUIYsSreoMI8w%0AG1Rk0que09LgP1RmCiSl1GUSTL%2FlrK%2FdYaw0orZwUxzKg%2FKNnTYprYiAIVRsHUz8%0Abkd6mGEBCfDdpEp0l7laBej2R8RhGDwuxjma1ZrwlCsKLlpdn2lwzqIEc%2BZl7dxi%0ALTb1NLMH80f4LCuF1iFCD6E%3D%0A-----END%20CERTIFICATE-----");
    }

    public WebClient getClient() {
        if (client == null) {
            client = WebClient.builder().baseUrl(authServiceBaseUrl).build();
        }
        return client;
    }

    public void setExcludedHeaders(List<String> excludedHeaders) {
        CzertainlyAuthenticationClient.excludedHeaders = excludedHeaders;
    }
}