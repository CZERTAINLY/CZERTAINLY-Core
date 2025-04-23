package com.czertainly.core.messaging.model;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import lombok.*;

import java.util.UUID;

@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EventMessage {

    private ResourceEvent resourceEvent;

    private Resource overrideResource;

    private UUID overrideObjectUuid;

    private Resource resource;

    private UUID objectUuid;

    private UUID userUuid;

}
