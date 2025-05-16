package com.czertainly.core.events;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.UniquelyIdentifiedObject;
import com.czertainly.core.dao.entity.workflows.TriggerAssociation;
import com.czertainly.core.evaluator.TriggerEvaluator;
import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.tasks.ScheduledJobInfo;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class EventContext<T extends UniquelyIdentifiedObject> {

    private final Resource resource;
    private final ResourceEvent resourceEvent;
    private final UUID userUuid;
    private final UUID associationObjectUuid;
    private final Object data;
    private final ScheduledJobInfo scheduledJobInfo;

    private final TriggerEvaluator<T> triggerEvaluator;
    private final List<T> resourceObjects = new ArrayList<>();
    private final List<Object> resourceObjectsEventData = new ArrayList<>();
    private final List<TriggerAssociation> triggers = new ArrayList<>();
    private final List<TriggerAssociation> ignoreTriggers = new ArrayList<>();

    public EventContext(EventMessage eventMessage, TriggerEvaluator<T> triggerEvaluator, T resourceObject, Object resourceObjectEventData) {
        this.resource = eventMessage.getResource();
        this.resourceEvent = eventMessage.getResourceEvent();
        this.userUuid = eventMessage.getUserUuid();
        this.associationObjectUuid = eventMessage.getOverrideObjectUuid();
        this.data = eventMessage.getData();
        this.scheduledJobInfo = eventMessage.getScheduledJobInfo();

        this.triggerEvaluator = triggerEvaluator;
        if (resourceObject != null) {
            this.resourceObjects.add(resourceObject);
            this.resourceObjectsEventData.add(resourceObjectEventData);
        }
    }
}
