
package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.auth.Resource;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "rule_action_group")
public class RuleActionGroup extends UniquelyIdentified{

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "resource", nullable = false)
    @Enumerated(EnumType.STRING)
    private Resource resource;

    @OneToMany(mappedBy = "ruleActionGroup", fetch = FetchType.LAZY)
    private List<RuleAction> actions;

    @ManyToMany(mappedBy = "actionGroups", fetch = FetchType.LAZY)
    @JsonBackReference
    private List<RuleTrigger> ruleTriggers;
}
