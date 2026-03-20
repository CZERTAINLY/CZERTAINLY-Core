package com.czertainly.core.cbom.event;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.czertainly.core.events.data.CbomRepositoryUrlChangedEvent;

@Component
public class CbomRepositoryUrlEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public CbomRepositoryUrlEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publishIfChanged(String oldUrl, String newUrl) {
        if (StringUtils.equals(oldUrl, newUrl)) {
            return;
        }
        applicationEventPublisher.publishEvent(new CbomRepositoryUrlChangedEvent(oldUrl, newUrl));
    }
}
