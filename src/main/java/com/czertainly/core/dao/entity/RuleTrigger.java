
package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.rules.RuleTriggerDetailDto;
import com.czertainly.api.model.core.rules.RuleTriggerDto;
import com.czertainly.api.model.core.rules.RuleTriggerType;
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

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "resource")
    @Enumerated(EnumType.STRING)
    private Resource resource;

    @Column(name = "event_name")
    private String eventName;

    @Column(name = "trigger_resource")
    @Enumerated(EnumType.STRING)
    private Resource triggerResource;

    @Column(name = "trigger_resource_uuid")
    private UUID triggerResourceUuid;

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

    public RuleTriggerDto mapToDto() {
        RuleTriggerDto ruleTriggerDto = new RuleTriggerDto();
        ruleTriggerDto.setUuid(uuid.toString());
        ruleTriggerDto.setName(name);
        ruleTriggerDto.setDescription(description);
        ruleTriggerDto.setResource(resource);
        ruleTriggerDto.setTriggerResource(triggerResource);
        ruleTriggerDto.setTriggerResourceUuid(triggerResourceUuid.toString());
        ruleTriggerDto.setTriggerType(triggerType);
        ruleTriggerDto.setEventName(eventName);
        return ruleTriggerDto;
    }

    public RuleTriggerDetailDto mapToDetailDto() {
        RuleTriggerDetailDto triggerDetailDto = new RuleTriggerDetailDto();
        triggerDetailDto.setUuid(uuid.toString());
        triggerDetailDto.setName(name);
        triggerDetailDto.setDescription(description);
        triggerDetailDto.setResource(resource);
        triggerDetailDto.setTriggerResource(triggerResource);
        triggerDetailDto.setTriggerResourceUuid(triggerResourceUuid.toString());
        triggerDetailDto.setTriggerType(triggerType);
        triggerDetailDto.setEventName(eventName);
        triggerDetailDto.setRules(rules.stream().map(Rule::mapToDto).toList());
        triggerDetailDto.setActionGroups(actionGroups.stream().map(RuleActionGroup::mapToDto).toList());
        triggerDetailDto.setActions(actions.stream().map(RuleAction::mapToDto).toList());
        return triggerDetailDto;
    }
}
