package com.czertainly.core.messaging.model;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.tasks.ScheduledJobInfo;
import lombok.*;

import java.util.UUID;

@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EventMessage {

    private ResourceEvent resourceEvent;
    private Resource resource;
    private UUID objectUuid;
    private Resource overrideResource;
    private UUID overrideObjectUuid;
    private Object data;
    private UUID userUuid;
    private ScheduledJobInfo scheduledJobInfo;

    public EventMessage(ResourceEvent resourceEvent, Resource resource, UUID objectUuid, Object data) {
        this.resourceEvent = resourceEvent;
        this.resource = resource;
        this.objectUuid = objectUuid;
        this.data = data;
    }
}
