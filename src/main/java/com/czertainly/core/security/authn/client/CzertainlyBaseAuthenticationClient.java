package com.czertainly.core.security.authn.client;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.model.core.connector.ConnectorDto;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Exceptions;

import java.util.function.Function;

@Component
public class CzertainlyBaseAuthenticationClient {

    private static final Log logger = LogFactory.getLog(CzertainlyBaseAuthenticationClient.class);

    @Value("${auth-service.base-url}")
    private String authServiceBaseUrl;


    private WebClient client;


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
        request = getClient().method(method);
        return (WebClient.RequestBodyUriSpec) request;

    }

    public WebClient getClient() {
        if (client == null) {
            client = WebClient.builder().baseUrl(authServiceBaseUrl).build();
        }
        return client;
    }
}