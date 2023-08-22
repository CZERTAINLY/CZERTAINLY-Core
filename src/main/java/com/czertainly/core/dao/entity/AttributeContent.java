package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "attribute_content")
public class AttributeContent extends UniquelyIdentified {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_definition_uuid", nullable = false, insertable = false, updatable = false)
    private AttributeDefinition attributeDefinition;

    @Column(name = "attribute_definition_uuid", nullable = false)
    private UUID attributeDefinitionUuid;

    @OneToMany(mappedBy = "attributeContent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AttributeContentItem> attributeContentItems;

    @OneToMany(mappedBy = "attributeContent", fetch = FetchType.LAZY)
    private List<AttributeContent2Object> attributeContent2Objects;

    public void setAttributeContentItems(List<AttributeContentItem> attributeContentItems) {
        this.attributeContentItems = attributeContentItems;
    }

    public AttributeDefinition getAttributeDefinition() {
        return attributeDefinition;
    }

    public void setAttributeDefinition(AttributeDefinition attributeDefinition) {
        this.attributeDefinition = attributeDefinition;
        this.attributeDefinitionUuid = attributeDefinition.getUuid();
    }

    public <T extends BaseAttributeContent> List<T> getAttributeContent() {
        return (List<T>) getAttributeContentItems().stream().map(AttributeContentItem::getJson).toList();
    }

    public void addAttributeContent(List<BaseAttributeContent> baseAttributeContents) {
        baseAttributeContents.forEach(bAttr -> getAttributeContentItems().add(new AttributeContentItem(this, bAttr)));
    }

    public List<AttributeContentItem> getAttributeContentItems() {
        if (attributeContentItems == null) {
            attributeContentItems = new ArrayList<>();
        }
        return attributeContentItems;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("attributeContent", attributeContentItems != null ? Arrays.toString(attributeContentItems.toArray()) : "-----")
                .append("uuid", uuid)
                .append("attributeDefinitionUuid", attributeDefinitionUuid)
                .toString();
    }
}
