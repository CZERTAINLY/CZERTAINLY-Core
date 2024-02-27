
package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.rules.RuleActionGroupDetailDto;
import com.czertainly.api.model.core.rules.RuleActionGroupDto;
import com.czertainly.api.model.core.rules.RuleConditionGroupDetailDto;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "rule_action_group")
public class RuleActionGroup extends UniquelyIdentified {

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

    public RuleActionGroupDto mapToDto() {
        RuleActionGroupDto ruleActionGroupDto = new RuleActionGroupDto();
        ruleActionGroupDto.setUuid(uuid.toString());
        ruleActionGroupDto.setName(name);
        ruleActionGroupDto.setDescription(description);
        ruleActionGroupDto.setResource(resource);
        return ruleActionGroupDto;
    }

    public RuleActionGroupDetailDto mapToDetailDto() {
        RuleActionGroupDetailDto actionGroupDetailDto = new RuleActionGroupDetailDto();
        actionGroupDetailDto.setUuid(uuid.toString());
        actionGroupDetailDto.setName(name);
        actionGroupDetailDto.setDescription(description);
        actionGroupDetailDto.setResource(resource);
        if (actions != null) actionGroupDetailDto.setActions(actions.stream().map(RuleAction::mapToDto).toList());
        return actionGroupDetailDto;
    }
}
