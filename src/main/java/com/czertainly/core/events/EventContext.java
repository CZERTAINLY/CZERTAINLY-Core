package com.czertainly.core.events;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.workflows.Trigger;
import com.czertainly.core.evaluator.RuleEvaluator;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class EventContext<T> {

    private final Resource resource;
    private final ResourceEvent resourceEvent;
    private final RuleEvaluator<T> ruleEvaluator;
    private final UUID userUuid;
    private final UUID associationObjectUuid;

    private final List<T> resourceObjects = new ArrayList<>();
    private final List<Trigger> triggers = new ArrayList<>();
    private final List<Trigger> ignoreTriggers = new ArrayList<>();

    public EventContext(Resource resource, ResourceEvent resourceEvent, RuleEvaluator<T> ruleEvaluator, UUID userUuid, UUID associationObjectUuid) {
        this.resource = resource;
        this.resourceEvent = resourceEvent;
        this.ruleEvaluator = ruleEvaluator;
        this.userUuid = userUuid;
        this.associationObjectUuid = associationObjectUuid;
    }
}
