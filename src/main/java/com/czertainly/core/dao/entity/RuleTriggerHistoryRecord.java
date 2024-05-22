package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.rules.RuleTriggerHistoryRecordDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
public class RuleTriggerHistoryRecord extends UniquelyIdentified {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trigger_history_uuid", nullable = false)
    private RuleTriggerHistory triggerHistory;

    @Column(name = "rule_condition_uuid")
    private UUID ruleConditionUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_condition_uuid", insertable = false, updatable = false)
    private RuleCondition condition;

    @Column(name = "rule_action_uuid")
    private UUID ruleActionUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_action_uuid", insertable = false, updatable = false)
    private RuleAction action;

    @Column(name = "message")
    private String message;

    public RuleTriggerHistoryRecordDto mapToDto() {
        RuleTriggerHistoryRecordDto triggerHistoryRecordDto = new RuleTriggerHistoryRecordDto();
        triggerHistoryRecordDto.setMessage(message);
        if (ruleActionUuid != null) triggerHistoryRecordDto.setAction(action.mapToDto());
        if (ruleConditionUuid != null) triggerHistoryRecordDto.setCondition(condition.mapToDto());
        return triggerHistoryRecordDto;
    }
}
