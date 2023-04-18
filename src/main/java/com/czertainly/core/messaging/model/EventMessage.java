package com.czertainly.core.messaging.model;

import com.czertainly.core.messaging.model.enums.EventTypeEnum;
import com.czertainly.core.messaging.model.enums.ResourceTypeEnum;
import com.czertainly.core.messaging.model.enums.ServiceEnum;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.UUID;

@NoArgsConstructor
@Getter(AccessLevel.PROTECTED)
@Setter(AccessLevel.PROTECTED)
public class EventMessage {

    @JsonProperty
    private ServiceEnum service;

    @JsonProperty
    private ResourceTypeEnum resource;

    @JsonProperty
    private UUID resourceUUID;

    @JsonProperty
    private EventTypeEnum type;

    @JsonProperty
    private String name;

    public EventMessage(ServiceEnum service, ResourceTypeEnum resource, EventTypeEnum type) {
        this.service = service;
        this.resource = resource;
        this.type = type;
    }

    public EventMessage(ResourceTypeEnum resource, UUID resourceUUID, String name) {
        this.service = ServiceEnum.CORE;
        this.type = EventTypeEnum.EVENTS;
        this.resource = resource;
        this.resourceUUID = resourceUUID;
        this.name = name;
    }

    @Override
    public String toString() {
        return String.format("EventMessage (%s, %s, %s, %s, %s)",
                service.getValue(),
                type.getValue(),
                resource.getValue(),
                resourceUUID,
                name);
    }
}
