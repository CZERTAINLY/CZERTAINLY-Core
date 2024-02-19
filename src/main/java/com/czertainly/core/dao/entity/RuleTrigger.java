
package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.rules.RuleTriggerType;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "rule_trigger")
public class RuleTrigger extends UniquelyIdentified {

    @Column(name = "trigger_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private RuleTriggerType triggerType;

    @Column(name = "event_name", nullable = false)
    private String eventName;

    @Column(name = "resource", nullable = false)
    @Enumerated(EnumType.STRING)
    private Resource resource;

    @Column(name = "resource_uuid")
    private UUID resourceUuid;


    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "rule_trigger_2_rule",
            joinColumns = @JoinColumn(name = "rule_trigger_uuid"),
            inverseJoinColumns = @JoinColumn(name = "rule_uuid"))
    private List<Rule> rules;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "rule_trigger_2_rule_action_group",
            joinColumns = @JoinColumn(name = "rule_trigger_uuid"),
            inverseJoinColumns = @JoinColumn(name = "rule_action_group_uuid"))
    private List<RuleActionGroup> actionGroups;

    @OneToMany(mappedBy = "ruleTrigger", fetch = FetchType.LAZY)
    private List<RuleAction> actions;
}
