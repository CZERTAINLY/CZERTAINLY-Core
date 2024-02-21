package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Getter
@Setter
@Table(name = "rule_condition")
public class RuleCondition extends UniquelyIdentified {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condition_group_uuid")
    private RuleConditionGroup ruleConditionGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_uuid")
    private Rule rule;

    @Column(name = "field_source", nullable = false)
    @Enumerated(EnumType.STRING)
    private FilterFieldSource fieldSource;

    @Column(name = "field_identifier", nullable = false)
    private String fieldIdentifier;

    @Column(name = "operator", nullable = false)
    @Enumerated(EnumType.STRING)
    private FilterConditionOperator operator;

    @Column(name = "value")
    private Serializable value;
}
