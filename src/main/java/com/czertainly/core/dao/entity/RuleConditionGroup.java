
package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.auth.Resource;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "rule_condition_group")
public class RuleConditionGroup extends UniquelyIdentified {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "resource")
    @Enumerated(EnumType.STRING)
    private Resource resource;

    @OneToMany(mappedBy = "ruleConditionGroup", fetch = FetchType.LAZY)
    private List<RuleCondition> conditions;

    @ManyToMany(mappedBy = "conditionGroups")
    @JsonBackReference
    private List<Rule> rules;
}
