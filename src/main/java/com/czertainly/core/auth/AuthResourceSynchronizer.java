package com.czertainly.core.auth;

import com.czertainly.core.model.auth.ResourceSyncRequestDto;
import com.czertainly.core.model.auth.SyncResponseDto;
import com.czertainly.core.security.authn.client.ResourceApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class AuthResourceSynchronizer {

    private static final Logger logger = LoggerFactory.getLogger(AuthResourceSynchronizer.class);
    private ResourceListener resourceListener;
    private ResourceApiClient resourceApiClient;

    @Autowired
    public void setEndpointsListener(ResourceListener resourceListener) {
        this.resourceListener = resourceListener;
    }

    @Autowired
    public void setEndPointApiClient(ResourceApiClient resourceApiClient) {
        this.resourceApiClient = resourceApiClient;
    }

    @EventListener({ApplicationReadyEvent.class})
    public void register() {
        logger.info("Initiating Endpoints sync");
        List<ResourceSyncRequestDto> resources = resourceListener.getResources();
        logger.debug("Resources: {}", resources);
        //Sync API Operation here
        SyncResponseDto response = resourceApiClient.syncResources(resources);
        logger.info("Sync operation completed, Response is {}", response);
    }
}
