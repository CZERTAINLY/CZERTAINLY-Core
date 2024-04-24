
package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.rules.RuleActionGroupDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    @OneToMany(mappedBy = "ruleActionGroup", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<RuleAction> actions;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "rule_trigger_2_rule_action_group",
            joinColumns = @JoinColumn(name = "rule_action_group_uuid"),
            inverseJoinColumns = @JoinColumn(name = "rule_trigger_uuid"))
    private List<RuleTrigger> ruleTriggers;

    public RuleActionGroupDto mapToDto() {
        RuleActionGroupDto ruleActionGroupDto = new RuleActionGroupDto();
        ruleActionGroupDto.setUuid(uuid.toString());
        ruleActionGroupDto.setName(name);
        ruleActionGroupDto.setDescription(description);
        ruleActionGroupDto.setResource(resource);
        return ruleActionGroupDto;
    }

}
