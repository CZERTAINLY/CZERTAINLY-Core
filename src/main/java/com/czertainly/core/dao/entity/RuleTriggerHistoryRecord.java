package com.czertainly.core.dao.entity;

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

    @Column(name = "rule_action_uuid")
    private UUID ruleActionUuid;

    @Column(name = "message")
    private String message;
}
