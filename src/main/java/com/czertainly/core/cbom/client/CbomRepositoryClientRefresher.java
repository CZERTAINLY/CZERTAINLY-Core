package com.czertainly.core.cbom.client;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.czertainly.core.events.data.CbomRepositoryUrlChangedEvent;

@Component
public class CbomRepositoryClientRefresher {

    private final CbomRepositoryClient cbomRepositoryClient;

    public CbomRepositoryClientRefresher(CbomRepositoryClient cbomRepositoryClient) {
        this.cbomRepositoryClient = cbomRepositoryClient;
    }

    @EventListener
    public void onCbomRepositoryUrlChanged(CbomRepositoryUrlChangedEvent event) {
        if (StringUtils.isBlank(event.newUrl())) {
            cbomRepositoryClient.resetClient();
            return;
        }

        cbomRepositoryClient.recreateClient(event.newUrl());
    }
}
