package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.rules.RuleActionType;
import com.czertainly.api.model.core.search.SearchCondition;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "rule_action")
public class RuleAction extends UniquelyIdentified{

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_group_uuid")
    private RuleActionGroup ruleActionGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trigger_uuid")
    private RuleTrigger ruleTrigger;

    @Column(name = "action_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private RuleActionType actionType;

    @Column(name = "search_group")
    private String search_group;

    @Column(name = "field_identifier")
    private String fieldIdentifier;

    @Column(name = "value")
    private Object value;
}
