
package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.rules.RuleDetailDto;
import com.czertainly.api.model.core.rules.RuleDto;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(name = "description")
    private String description;

    @Column(name = "resource", nullable = false)
    @Enumerated(EnumType.STRING)
    private Resource resource;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_format")
    private String resourceFormat;

    @Column(name = "attributes", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<BaseAttribute> attributes;


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


    public RuleDto mapToDto() {
        RuleDto ruleDto = new RuleDto();
        ruleDto.setUuid(uuid.toString());
        ruleDto.setName(name);
        ruleDto.setDescription(description);
        if (connectorUuid != null) ruleDto.setConnector_uuid(connectorUuid.toString());
        ruleDto.setResource(resource);
        ruleDto.setResourceType(resourceType);
        ruleDto.setResourceFormat(resourceFormat);
        ruleDto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(attributes));
        return ruleDto;
    }

    public RuleDetailDto mapToDetailDto() {

        RuleDetailDto ruleDetailDto = new RuleDetailDto();
        ruleDetailDto.setUuid(uuid.toString());
        ruleDetailDto.setName(name);
        ruleDetailDto.setDescription(description);
        if (connectorUuid != null) ruleDetailDto.setConnector_uuid(connectorUuid.toString());
        ruleDetailDto.setResource(resource);
        ruleDetailDto.setResourceType(resourceType);
        ruleDetailDto.setResourceFormat(resourceFormat);
        ruleDetailDto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(attributes));
        if (conditions != null) ruleDetailDto.setConditions(conditions.stream().map(RuleCondition::mapToDto).toList());
        ruleDetailDto.setConditionGroups(conditionGroups.stream().map(RuleConditionGroup::mapToDto).toList());
        return ruleDetailDto;
    }

}
