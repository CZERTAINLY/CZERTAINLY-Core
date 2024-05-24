
package com.czertainly.core.dao.entity.workflows;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.workflows.RuleDetailDto;
import com.czertainly.api.model.core.workflows.RuleDto;
import com.czertainly.core.dao.entity.UniquelyIdentified;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "rule")
public class Rule extends UniquelyIdentified {
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "resource", nullable = false)
    @Enumerated(EnumType.STRING)
    private Resource resource;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "rule_2_condition",
            joinColumns = @JoinColumn(name = "rule_uuid"),
            inverseJoinColumns = @JoinColumn(name = "condition_uuid"))
    private List<Condition> conditions;

    public RuleDto mapToDto() {
        RuleDto ruleDto = new RuleDto();
        ruleDto.setUuid(uuid.toString());
        ruleDto.setName(name);
        ruleDto.setDescription(description);
        ruleDto.setResource(resource);
        return ruleDto;
    }

    public RuleDetailDto mapToDetailDto() {
        RuleDetailDto ruleDetailDto = new RuleDetailDto();
        ruleDetailDto.setUuid(uuid.toString());
        ruleDetailDto.setName(name);
        ruleDetailDto.setDescription(description);
        ruleDetailDto.setResource(resource);
        if (conditions != null) ruleDetailDto.setConditions(conditions.stream().map(Condition::mapToDto).toList());
        
        return ruleDetailDto;
    }

}
