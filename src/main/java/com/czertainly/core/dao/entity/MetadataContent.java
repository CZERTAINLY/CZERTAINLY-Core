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
@Table(name = "metadata_content")
public class MetadataContent extends UniquelyIdentifiedAndAudited {

    @ManyToOne
    @JoinColumn(name = "metadata_definition_uuid", nullable = false, insertable = false, updatable = false)
    private MetadataDefinition metadataDefinition;

    @Column(name = "metadata_definition_uuid", nullable = false)
    private UUID metadataDefinitionUuid;

    @Column(name = "attribute_content")
    private String attributeContent;

    public MetadataDefinition getMetadataDefinition() {
        return metadataDefinition;
    }

    public void setMetadataDefinition(MetadataDefinition metadataDefinition) {
        this.metadataDefinition = metadataDefinition;
        this.metadataDefinitionUuid = metadataDefinition.getUuid();
    }

    public UUID getMetadataDefinitionUuid() {
        return metadataDefinitionUuid;
    }

    public void setMetadataDefinitionUuid(UUID metadataDefinitionUuid) {
        this.metadataDefinitionUuid = metadataDefinitionUuid;
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
                .append("attribute_content", attributeContent)
                .append("uuid", uuid)
                .append("metadataDefinitionUuid", metadataDefinitionUuid)
                .toString();
    }
}
