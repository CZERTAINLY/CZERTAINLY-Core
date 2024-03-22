package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "attribute_content_item")
public class AttributeContentItem extends UniquelyIdentified {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_definition_uuid", nullable = false, insertable = false, updatable = false)
    private AttributeDefinition attributeDefinition;

    @Column(name = "attribute_definition_uuid", nullable = false)
    private UUID attributeDefinitionUuid;

    @Column(name = "json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private BaseAttributeContent<?> json;

    @JsonBackReference
    @OneToMany(mappedBy = "attributeContentItem", fetch = FetchType.LAZY)
    private List<AttributeContent2Object> objects;

    public void setAttributeDefinition(AttributeDefinition attributeDefinition) {
        this.attributeDefinition = attributeDefinition;
        this.attributeDefinitionUuid = attributeDefinition.getUuid();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("uuid", uuid)
                .append("attributeDefinitionUuid", attributeDefinitionUuid)
                .append("json", json)
                .toString();
    }
}
