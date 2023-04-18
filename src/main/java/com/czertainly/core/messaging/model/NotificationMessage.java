package com.czertainly.core.messaging.model;

import com.czertainly.core.messaging.model.enums.EventTypeEnum;

public class NotificationMessage extends EventMessage {

    public NotificationMessage(EventMessage eventMessage) {
        super(eventMessage.getService(), eventMessage.getResource(), EventTypeEnum.NOTIFICATION);
        setResourceUUID(eventMessage.getResourceUUID());
        setName(eventMessage.getName());
    }
}
