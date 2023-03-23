package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "attribute_content_item")
public class AttributeContentItem extends UniquelyIdentified {

    @ManyToOne
    @JoinColumn(name = "attribute_content_uuid", nullable = false, insertable = false, updatable = false)
    private AttributeContent attributeContent;

    @Column(name = "attribute_content_uuid", nullable = false)
    private UUID attributeContentUuid;

    @Column(name = "json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private BaseAttributeContent json;

    public AttributeContentItem() {
    }

    public AttributeContentItem(AttributeContent attributeContent, UUID attributeContentUuid, BaseAttributeContent json) {
        this.attributeContent = attributeContent;
        this.attributeContentUuid = attributeContentUuid;
        this.json = json;
    }

    public UUID getAttributeContentUuid() {
        return attributeContentUuid;
    }

    public void setAttributeContentUuid(UUID attributeContentUuid) {
        this.attributeContentUuid = attributeContentUuid;
    }

    public AttributeContent getAttributeContent() {
        return attributeContent;
    }

    public void setAttributeContent(AttributeContent attributeContent) {
        this.attributeContent = attributeContent;
    }

    public void setJson(BaseAttributeContent json) {
        this.json = json;
    }

    public BaseAttributeContent getJson() {
        return json;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("attributeContent", attributeContent)
                .append("uuid", uuid)
                .append("attributeContentUuid", attributeContent.getUuid())
                .toString();
    }
}
