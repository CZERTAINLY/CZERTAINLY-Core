package com.czertainly.core.dao.entity.workflows;

import com.czertainly.api.model.core.workflows.ConditionItemDto;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.core.dao.entity.UniquelyIdentified;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Setter
@Table(name = "condition_item")
public class ConditionItem extends UniquelyIdentified {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condition_uuid", nullable = false)
    private Condition condition;

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

    public ConditionItemDto mapToDto() {
        ConditionItemDto conditionItemDto = new ConditionItemDto();
        conditionItemDto.setFieldSource(fieldSource);
        conditionItemDto.setFieldIdentifier(fieldIdentifier);
        conditionItemDto.setOperator(operator);
        conditionItemDto.setValue(value);

        return conditionItemDto;
    }
}
