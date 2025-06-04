package com.czertainly.core.events;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.UniquelyIdentifiedObject;
import com.czertainly.core.evaluator.TriggerEvaluator;
import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.tasks.ScheduledJobInfo;
import lombok.Getter;

import java.util.*;

@Getter
public class EventContext<T extends UniquelyIdentifiedObject> {

    private final Resource resource;
    private final ResourceEvent event;
    private final UUID userUuid;
    private final UUID associationObjectUuid;
    private final Object data;
    private final ScheduledJobInfo scheduledJobInfo;

    private final TriggerEvaluator<T> triggerEvaluator;
    private final List<T> resourceObjects = new ArrayList<>();
    private final List<Object> resourceObjectsEventData = new ArrayList<>();
    private final EventContextTriggers platformTriggers;
    private final Map<String, EventContextTriggers> overridingResourceTriggers = new HashMap<>();

    public EventContext(EventMessage eventMessage, TriggerEvaluator<T> triggerEvaluator, T resourceObject, Object resourceObjectEventData) {
        this.resource = eventMessage.getResource();
        this.event = eventMessage.getEvent();
        this.userUuid = eventMessage.getUserUuid();
        this.associationObjectUuid = eventMessage.getOverrideObjectUuid();
        this.data = eventMessage.getData();
        this.scheduledJobInfo = eventMessage.getScheduledJobInfo();
        this.platformTriggers = new EventContextTriggers(null, null);

        this.triggerEvaluator = triggerEvaluator;
        if (resourceObject != null) {
            this.resourceObjects.add(resourceObject);
            this.resourceObjectsEventData.add(resourceObjectEventData);
        }
    }
}
