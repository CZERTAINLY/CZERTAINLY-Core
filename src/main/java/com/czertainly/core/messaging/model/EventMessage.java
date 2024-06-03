package com.czertainly.core.messaging.model;

import com.czertainly.api.model.core.auth.Resource;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.UUID;

@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EventMessage {

    private Resource resource;

    private UUID resourceUUID;

    private String eventName;

    private String eventStatus;

    private String eventMessage;

    private String eventDetail;

    private UUID userUuid;

}
