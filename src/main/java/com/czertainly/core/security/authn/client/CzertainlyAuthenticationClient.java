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
            WebClient.RequestHeadersSpec<WebClient.RequestBodySpec> request = getClient()
                    .post()
                    .uri("/auth")
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

    private void insertHeaders(HttpHeaders headers, WebClient.RequestHeadersSpec<WebClient.RequestBodySpec> request) {
        headers.forEach((name, value) -> {
            if (!excludedHeaders.contains(name)) {
                request.header(name, String.join(",", value));
            }
        });
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