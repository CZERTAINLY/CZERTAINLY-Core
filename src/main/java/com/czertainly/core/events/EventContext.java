package com.czertainly.core.events;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.UniquelyIdentifiedObject;
import com.czertainly.core.dao.entity.workflows.Trigger;
import com.czertainly.core.evaluator.RuleEvaluator;
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

    private final RuleEvaluator<T> ruleEvaluator;
    private final List<T> resourceObjects = new ArrayList<>();
    private final List<Trigger> triggers = new ArrayList<>();
    private final List<Trigger> ignoreTriggers = new ArrayList<>();

    public EventContext(EventMessage eventMessage, RuleEvaluator<T> ruleEvaluator, T resourceObject) {
        this.resource = eventMessage.getResource();
        this.resourceEvent = eventMessage.getResourceEvent();
        this.userUuid = eventMessage.getUserUuid();
        this.associationObjectUuid = eventMessage.getOverrideObjectUuid();
        this.data = eventMessage.getData();
        this.scheduledJobInfo = eventMessage.getScheduledJobInfo();

        this.ruleEvaluator = ruleEvaluator;
        if (resourceObject != null) {
            this.resourceObjects.add(resourceObject);
        }
    }
}
