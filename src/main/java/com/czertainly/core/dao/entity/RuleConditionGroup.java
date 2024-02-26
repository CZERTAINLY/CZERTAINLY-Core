
package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.rules.RuleConditionGroupDetailDto;
import com.czertainly.api.model.core.rules.RuleConditionGroupDto;
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

    @Column(name = "resource", nullable = false)
    @Enumerated(EnumType.STRING)
    private Resource resource;

    @OneToMany(mappedBy = "ruleConditionGroup", fetch = FetchType.LAZY)
    private List<RuleCondition> conditions;

    @ManyToMany(mappedBy = "conditionGroups", fetch = FetchType.LAZY)
    @JsonBackReference
    private List<Rule> rules;

    public RuleConditionGroupDto mapToDto() {
        RuleConditionGroupDto ruleConditionGroupDto = new RuleConditionGroupDto();
        ruleConditionGroupDto.setUuid(uuid.toString());
        ruleConditionGroupDto.setName(name);
        ruleConditionGroupDto.setDescription(description);
        ruleConditionGroupDto.setResource(resource);
        return ruleConditionGroupDto;
    }

    public RuleConditionGroupDetailDto mapToDetailDto() {
        RuleConditionGroupDetailDto ruleConditionGroupDetailDto = new RuleConditionGroupDetailDto();
        ruleConditionGroupDetailDto.setUuid(uuid.toString());
        ruleConditionGroupDetailDto.setName(name);
        ruleConditionGroupDetailDto.setDescription(description);
        ruleConditionGroupDetailDto.setResource(resource);
        if (conditions != null) ruleConditionGroupDetailDto.setConditions(conditions.stream().map(RuleCondition::mapToDto).toList());
        return ruleConditionGroupDetailDto;
    }

}
