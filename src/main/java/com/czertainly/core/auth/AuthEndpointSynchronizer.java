package com.czertainly.core.auth;

import com.czertainly.core.model.auth.SyncRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class AuthEndpointSynchronizer {

    private static final Logger logger = LoggerFactory.getLogger(AuthEndpointSynchronizer.class);
    private EndpointsListener endpointsListener;
    @Value("${auth-service.host:http://authorization-provider-service:8080}")
    private String authorizationServiceEndpoint;
    @Value("${auth-service.endpoint-sync-uri:/auth/v1/endpoints/sync}")
    private String syncContext;

    @Autowired
    public void setEndpointsListener(EndpointsListener endpointsListener) {
        this.endpointsListener = endpointsListener;
    }

    @EventListener({ApplicationReadyEvent.class})
    public void register() {
        logger.info("Initiating Endpoints sync");
        List<SyncRequestDto> endpoints = endpointsListener.getEndpoints();
        logger.debug("Endpoints: {}", endpoints);
        String baseURI = authorizationServiceEndpoint + syncContext;
        logger.info("Endpoint Sync URI: {}", baseURI);
        //Sync API Operation here
        logger.info("Sync operation completed");
    }
}
