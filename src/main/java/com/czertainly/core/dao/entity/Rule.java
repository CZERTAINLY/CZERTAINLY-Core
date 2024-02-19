
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
@Table(name = "rule")
public class Rule extends UniquelyIdentified {

    @Column(name = "connector_uuid")
    private UUID connectorUuid;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "resource", nullable = false)
    @Enumerated(EnumType.STRING)
    private Resource resource;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_format")
    private String resourceFormat;

    @Column(name = "attributes")
    private String attributes;

    @OneToMany(mappedBy = "rule", fetch = FetchType.LAZY)
    @JsonBackReference
    private List<RuleCondition> conditions;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "rule_2_rule_condition_group",
            joinColumns = @JoinColumn(name = "rule_uuid"),
            inverseJoinColumns = @JoinColumn(name = "rule_condition_group_uuid"))
    private List<RuleConditionGroup> conditionGroups;

    @ManyToMany(mappedBy = "rules", fetch = FetchType.LAZY)
    @JsonBackReference
    private List<RuleTrigger> ruleTriggers;
}
