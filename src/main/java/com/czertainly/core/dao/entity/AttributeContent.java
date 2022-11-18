package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "attribute_content")
public class AttributeContent extends UniquelyIdentified {

    @ManyToOne
    @JoinColumn(name = "attribute_definition_uuid", nullable = false, insertable = false, updatable = false)
    private AttributeDefinition attributeDefinition;

    @Column(name = "attribute_definition_uuid", nullable = false)
    private UUID attributeDefinitionUuid;

    @Column(name = "attribute_content")
    private String attributeContent;

    public AttributeDefinition getAttributeDefinition() {
        return attributeDefinition;
    }

    public void setAttributeDefinition(AttributeDefinition attributeDefinition) {
        this.attributeDefinition = attributeDefinition;
        this.attributeDefinitionUuid = attributeDefinition.getUuid();
    }

    public UUID getAttributeDefinitionUuid() {
        return attributeDefinitionUuid;
    }

    public void setAttributeDefinitionUuid(UUID attributeDefinitionUuid) {
        this.attributeDefinitionUuid = attributeDefinitionUuid;
    }

    public String getAttributeContentAsString() {
        return attributeContent;
    }

    public <T extends BaseAttributeContent> List<T> getAttributeContent(Class<T> clazz) {
        return AttributeDefinitionUtils.deserializeAttributeContent(attributeContent, clazz);
    }

    public void setAttributeContent(String attributeContent) {
        this.attributeContent = attributeContent;
    }

    public void setAttributeContent(List<BaseAttributeContent> attributeContent) {
        this.attributeContent = AttributeDefinitionUtils.serializeAttributeContent(attributeContent);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("attributeContent", attributeContent)
                .append("uuid", uuid)
                .append("attributeDefinitionUuid", attributeDefinitionUuid)
                .toString();
    }
}
