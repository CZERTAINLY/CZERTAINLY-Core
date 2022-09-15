package com.czertainly.core.auth;

import com.czertainly.core.model.auth.SyncRequestDto;
import com.czertainly.core.model.auth.SyncResponseDto;
import com.czertainly.core.security.authn.client.EndPointApiClient;
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
    private EndPointApiClient endPointApiClient;

    @Autowired
    public void setEndpointsListener(EndpointsListener endpointsListener) {
        this.endpointsListener = endpointsListener;
    }

    @Autowired
    public void setEndPointApiClient(EndPointApiClient endPointApiClient) {
        this.endPointApiClient = endPointApiClient;
    }

    @EventListener({ApplicationReadyEvent.class})
    public void register() {
        logger.info("Initiating Endpoints sync");
        List<SyncRequestDto> endpoints = endpointsListener.getEndpoints();
        logger.debug("Endpoints: {}", endpoints);
        //Sync API Operation here
//        SyncResponseDto response = endPointApiClient.syncEndPoints(endpoints);
//        logger.info("Sync operation completed, Response is {}", response);
    }
}
