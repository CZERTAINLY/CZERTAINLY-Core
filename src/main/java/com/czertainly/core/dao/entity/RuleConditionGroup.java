
package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.rules.RuleConditionGroupDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "rule_condition_group")
public class RuleConditionGroup extends UniquelyIdentified {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "resource", nullable = false)
    @Enumerated(EnumType.STRING)
    private Resource resource;

    @OneToMany(mappedBy = "ruleConditionGroup", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<RuleCondition> conditions;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "rule_2_rule_condition_group",
            joinColumns = @JoinColumn(name = "rule_condition_group_uuid"),
            inverseJoinColumns = @JoinColumn(name = "rule_uuid"))
    private List<Rule> rules;

    public RuleConditionGroupDto mapToDto() {
        RuleConditionGroupDto ruleConditionGroupDto = new RuleConditionGroupDto();
        ruleConditionGroupDto.setUuid(uuid.toString());
        ruleConditionGroupDto.setName(name);
        ruleConditionGroupDto.setDescription(description);
        ruleConditionGroupDto.setResource(resource);
        if (conditions != null) ruleConditionGroupDto.setConditions(conditions.stream().map(RuleCondition::mapToDto).toList());
        return ruleConditionGroupDto;
    }

}
