package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.rules.RuleActionType;
import com.czertainly.api.model.core.search.FilterFieldSource;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(name = "field_source")
    private FilterFieldSource fieldSource;

    @Column(name = "field_identifier")
    private String fieldIdentifier;

    @Column(name = "action_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Object actionData;
}
