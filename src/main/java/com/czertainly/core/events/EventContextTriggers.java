package com.czertainly.core.events;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.workflows.TriggerAssociation;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class EventContextTriggers {

    private final Resource resource;
    private final UUID objectUuid;
    private final List<TriggerAssociation> triggers = new ArrayList<>();
    private final List<TriggerAssociation> ignoreTriggers = new ArrayList<>();

    public EventContextTriggers(Resource resource, UUID objectUuid) {
        this.resource = resource;
        this.objectUuid = objectUuid;
    }
}
