package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.search.SearchCondition;
import com.czertainly.api.model.core.search.SearchGroup;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

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

    @Column(name = "search_group")
    @Enumerated(EnumType.STRING)
    private SearchGroup search_group;

    @Column(name = "field_identifier")
    private String fieldIdentifier;

    @Column(name = "operator")
    @Enumerated(EnumType.STRING)
    private SearchCondition operator;

    @Column(name = "value")
    private Object value;
}
