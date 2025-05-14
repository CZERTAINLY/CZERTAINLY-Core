package com.czertainly.core.dao.entity.workflows;

import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.workflows.ExecutionItemDto;
import com.czertainly.core.dao.converter.ObjectToJsonConverter;
import com.czertainly.core.dao.entity.UniquelyIdentified;
import com.czertainly.core.util.AttributeDefinitionUtils;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "execution_item")
public class ExecutionItem extends UniquelyIdentified {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_uuid", nullable = false)
    @ToString.Exclude
    private Execution execution;

    @Column(name = "field_source")
    @Enumerated(EnumType.STRING)
    private FilterFieldSource fieldSource;

    @Column(name = "field_identifier")
    private String fieldIdentifier;

    @Column(name = "data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = ObjectToJsonConverter.class)
    private Object data;

    public ExecutionItemDto mapToDto() {
        ExecutionItemDto actionDto = new ExecutionItemDto();
        actionDto.setFieldSource(fieldSource);
        actionDto.setFieldIdentifier(fieldIdentifier);
        if (fieldSource != FilterFieldSource.CUSTOM) {
            actionDto.setData((Serializable) data);
        } else {
            List<BaseAttributeContent> contentItems = AttributeDefinitionUtils.convertContentItemsFromObject(data);
            actionDto.setData((Serializable) (contentItems.size() == 1 ? contentItems.get(0).getData().toString() : contentItems.stream().map(i -> i.getData().toString()).toList()));
        }

        return actionDto;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
