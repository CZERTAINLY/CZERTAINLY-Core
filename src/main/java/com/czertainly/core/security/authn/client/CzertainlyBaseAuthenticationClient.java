package com.czertainly.core.security.authn.client;

import com.czertainly.core.security.exception.AuthenticationServiceException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import java.util.function.Function;

@Component
public class CzertainlyBaseAuthenticationClient {

    private static final Log logger = LogFactory.getLog(CzertainlyBaseAuthenticationClient.class);

    @Value("${auth-service.base-url}")
    private String authServiceBaseUrl;


    private WebClient client;

    public CzertainlyBaseAuthenticationClient(String authServiceBaseUrl, WebClient client) {
        this.authServiceBaseUrl = authServiceBaseUrl;
        this.client = client;
    }

    public CzertainlyBaseAuthenticationClient() {
    }

    public static <T, R> R processRequest(Function<T, R> func, T request) {
        try {
            return func.apply(request);
        } catch (Exception e) {
            Throwable unwrapped = Exceptions.unwrap(e);
            logger.error(unwrapped.getMessage(), unwrapped);
            throw e;
        }
    }

    public WebClient.RequestBodyUriSpec prepareRequest(HttpMethod method) {
        WebClient.RequestBodySpec request;
        request = getClient(null).method(method);
        return (WebClient.RequestBodyUriSpec) request;

    }

    public WebClient getClient(String customAuthServiceUrl) {
        if (client == null) {
            if(customAuthServiceUrl != null){
                client = WebClient
                        .builder()
                        .filter(ExchangeFilterFunction.ofResponseProcessor(CzertainlyBaseAuthenticationClient::handleHttpExceptions))
                        .baseUrl(customAuthServiceUrl)
                        .build();
            } else {
                client = WebClient
                        .builder()
                        .filter(ExchangeFilterFunction.ofResponseProcessor(CzertainlyBaseAuthenticationClient::handleHttpExceptions))
                        .baseUrl(authServiceBaseUrl)
                        .build();
            }
        }
        return client;
    }

    private static Mono<ClientResponse> handleHttpExceptions(ClientResponse clientResponse) {

        if (HttpStatus.INTERNAL_SERVER_ERROR.equals(clientResponse.statusCode())) {
            return clientResponse.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new AuthenticationServiceException(500, "Internal Server Error from Auth Service")));
        }

        if (clientResponse.statusCode().isError()) {
            return clientResponse.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new AuthenticationServiceException(body, true)));
        }

        return Mono.just(clientResponse);
    }
}