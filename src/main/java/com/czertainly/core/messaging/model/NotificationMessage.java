package com.czertainly.core.messaging.model;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;
import java.util.UUID;

@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationMessage {

    private ResourceEvent event;

    private Resource resource;

    private UUID objectUuid;

    private UUID notificationProfileUuid;

    private List<NotificationRecipient> recipients;

    private Object data;

    public NotificationMessage(ResourceEvent event, Resource resource, UUID objectUuid, UUID notificationProfileUuid, List<NotificationRecipient> recipients, Object data) {
        this.event = event;
        this.resource = resource;
        this.objectUuid = objectUuid;
        this.notificationProfileUuid = notificationProfileUuid;
        this.recipients = recipients;
        this.data = data;
    }

}
