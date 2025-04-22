package com.czertainly.core.events;

import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.workflows.Trigger;
import com.czertainly.core.evaluator.RuleEvaluator;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class EventContext<T> {

    private ResourceEvent resourceEvent;
    private RuleEvaluator<T> ruleEvaluator;

    private List<Trigger> triggers = new ArrayList<>();
    private List<Trigger> ignoreTriggers = new ArrayList<>();

    public EventContext(ResourceEvent resourceEvent, RuleEvaluator<T> ruleEvaluator) {
        this.resourceEvent = resourceEvent;
        this.ruleEvaluator = ruleEvaluator;
    }
}
