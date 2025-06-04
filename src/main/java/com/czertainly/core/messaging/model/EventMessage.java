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

    private ResourceEvent event;
    private Resource resource;
    private UUID objectUuid;
    private Resource overrideResource;
    private UUID overrideObjectUuid;
    private Object data;
    private UUID userUuid;
    private ScheduledJobInfo scheduledJobInfo;

    public EventMessage(ResourceEvent event, Resource resource, UUID objectUuid, Object data) {
        this.event = event;
        this.resource = resource;
        this.objectUuid = objectUuid;
        this.data = data;
    }
}
