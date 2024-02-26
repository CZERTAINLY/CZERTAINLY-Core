package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.rules.RuleActionDto;
import com.czertainly.api.model.core.rules.RuleActionType;
import com.czertainly.api.model.core.search.FilterFieldSource;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;

@Entity
@Getter
@Setter
@Table(name = "rule_action")
public class RuleAction extends UniquelyIdentified{

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_group_uuid")
    private RuleActionGroup ruleActionGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trigger_uuid")
    private RuleTrigger ruleTrigger;

    @Column(name = "action_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private RuleActionType actionType;

    @Column(name = "field_source")
    @Enumerated(EnumType.STRING)
    private FilterFieldSource fieldSource;

    @Column(name = "field_identifier")
    private String fieldIdentifier;

    @Column(name = "action_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Object actionData;

    public RuleActionDto mapToDto() {
        RuleActionDto actionDto = new RuleActionDto();
        actionDto.setUuid(uuid.toString());
        actionDto.setActionType(actionType);
        actionDto.setFieldSource(fieldSource);
        actionDto.setFieldIdentifier(fieldIdentifier);
        actionDto.setActionData((Serializable) actionData);
        return actionDto;
    }
}
