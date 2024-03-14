package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.core.rules.RuleConditionDto;
import com.czertainly.api.model.core.search.FilterConditionOperator;
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
@Table(name = "rule_condition")
public class RuleCondition extends UniquelyIdentified {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condition_group_uuid")
    private RuleConditionGroup ruleConditionGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_uuid")
    private Rule rule;

    @Column(name = "field_source", nullable = false)
    @Enumerated(EnumType.STRING)
    private FilterFieldSource fieldSource;

    @Column(name = "field_identifier", nullable = false)
    private String fieldIdentifier;

    @Column(name = "operator", nullable = false)
    @Enumerated(EnumType.STRING)
    private FilterConditionOperator operator;

    @Column(name = "value", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Object value;

    public RuleConditionDto mapToDto() {
        RuleConditionDto ruleConditionDto = new RuleConditionDto();
        ruleConditionDto.setUuid(uuid.toString());
        ruleConditionDto.setFieldSource(fieldSource);
        ruleConditionDto.setFieldIdentifier(fieldIdentifier);
        ruleConditionDto.setOperator(operator);
        ruleConditionDto.setValue(value);
        return ruleConditionDto;
    }

    public SearchFilterRequestDto mapToSearchFilterRequestDto() {
        SearchFilterRequestDto searchFilterRequestDto = new SearchFilterRequestDto();
        searchFilterRequestDto.setFieldSource(fieldSource);
        searchFilterRequestDto.setFieldIdentifier(fieldIdentifier);
        searchFilterRequestDto.setCondition(operator);
        searchFilterRequestDto.setValue((Serializable) value);
        return searchFilterRequestDto;
    }
}
