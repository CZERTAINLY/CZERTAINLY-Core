
package com.czertainly.core.dao.entity;

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
public class RuleTrigger extends UniquelyIdentified{

    @Column(name = "trigger_type", nullable = false)
    private String triggerType;

    @Column(name = "event_name", nullable = false)
    private String eventName;

    @Column(name = "resource", nullable = false)
    private String resource;

    @Column(name = "resource_uuid")
    private UUID resourceUuid;


    @OneToMany(mappedBy = "rule_trigger", fetch = FetchType.LAZY)
    @JsonBackReference
    private List<Rule> rules;

    @OneToMany(mappedBy = "rule_trigger", fetch = FetchType.LAZY)
    @JsonBackReference
    private List<RuleActionGroup> actionGroups;

    @OneToMany(mappedBy = "rule_trigger", fetch = FetchType.LAZY)
    @JsonBackReference
    private List<RuleAction> actions;
}
