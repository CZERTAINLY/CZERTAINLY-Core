package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.attribute.AttributeDefinitionDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeDefinitionDetailDto;
import com.czertainly.api.model.client.attribute.metadata.GlobalMetadataDefinitionDetailDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.CustomAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.InfoAttribute;
import com.czertainly.api.model.common.attribute.v2.MetadataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.properties.CustomAttributeProperties;
import com.czertainly.api.model.common.attribute.v2.properties.MetadataAttributeProperties;
import com.czertainly.core.util.AttributeDefinitionUtils;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.UUID;

@Entity
@Table(name = "attribute_definition")
public class AttributeDefinition extends UniquelyIdentifiedAndAudited {

    @OneToOne
    @JoinColumn(name = "connector_uuid", insertable = false, updatable = false)
    private Connector connector;

    @Column(name = "connector_uuid")
    private UUID connectorUuid;

    @Column(name = "attribute_uuid")
    private UUID attributeUuid;

    @Column(name = "attribute_name")
    private String attributeName;

    @Column(name = "attribute_definition", columnDefinition = "TEXT")
    private String attributeDefinition;

    @Column(name = "attribute_type")
    @Enumerated(EnumType.STRING)
    private AttributeType type;

    @Column(name = "attribute_content_type")
    @Enumerated(EnumType.STRING)
    private AttributeContentType contentType;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "global")
    private Boolean global;

    // This is the reference field. This field states that of the Attribute is stored just for the reference incase of the
    // response from group attributes
    @Column(name = "reference")
    private Boolean reference;

    public Connector getConnector() {
        return connector;
    }

    public void setConnector(Connector connector) {
        this.connector = connector;
        if (connector != null) this.connectorUuid = connector.getUuid();
    }

    public UUID getConnectorUuid() {
        return connectorUuid;
    }

    public void setConnectorUuid(UUID connectorUuid) {
        this.connectorUuid = connectorUuid;
    }

    public UUID getAttributeUuid() {
        return attributeUuid;
    }

    public void setAttributeUuid(UUID attributeUuid) {
        this.attributeUuid = attributeUuid;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    public String getAttributeDefinitionAsString() {
        return attributeDefinition;
    }

    public <T extends BaseAttribute> T getAttributeDefinition(Class<T> clazz) {
        return AttributeDefinitionUtils.deserializeSingleAttribute(attributeDefinition, clazz);
    }

    public void setAttributeDefinition(String attributeDefinition) {
        this.attributeDefinition = attributeDefinition;
    }

    public void setAttributeDefinition(InfoAttribute attributeDefinition) {
        this.attributeDefinition = AttributeDefinitionUtils.serialize(attributeDefinition);
    }

    public void setAttributeDefinition(MetadataAttribute attributeDefinition) {
        this.attributeDefinition = AttributeDefinitionUtils.serialize(attributeDefinition);
    }

    public void setAttributeDefinition(CustomAttribute attributeDefinition) {
        this.attributeDefinition = AttributeDefinitionUtils.serialize(attributeDefinition);
    }

    public void setAttributeDefinition(DataAttribute attributeDefinition) {
        this.attributeDefinition = AttributeDefinitionUtils.serialize(attributeDefinition);
    }

    public AttributeType getType() {
        return type;
    }

    public void setType(AttributeType type) {
        this.type = type;
    }

    public AttributeContentType getContentType() {
        return contentType;
    }

    public void setContentType(AttributeContentType contentType) {
        this.contentType = contentType;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean isGlobal() {
        return global;
    }

    public void setGlobal(Boolean global) {
        this.global = global;
    }

    public Boolean isReference() {
        return reference;
    }

    public void setReference(Boolean reference) {
        this.reference = reference;
    }

    public AttributeDefinitionDto mapToListDto(AttributeType type) {
        AttributeDefinitionDto dto = new AttributeDefinitionDto();
        if (type.equals(AttributeType.CUSTOM)) {
            CustomAttribute attribute = getAttributeDefinition(CustomAttribute.class);
            dto.setUuid(attribute.getUuid());
            dto.setName(attribute.getName());
            dto.setDescription(attribute.getDescription());
            dto.setContentType(attribute.getContentType());
            dto.setEnabled(enabled);
        } else if (type.equals(AttributeType.META)) {
            MetadataAttribute attribute = getAttributeDefinition(MetadataAttribute.class);
            dto.setUuid(uuid.toString());
            dto.setName(attribute.getName());
            dto.setContentType(attribute.getContentType());
            dto.setDescription(attribute.getDescription());
        } else {
            return null;
        }
        return dto;
    }

    public CustomAttributeDefinitionDetailDto mapToCustomAttributeDefinitionDetailDto() {
        CustomAttribute attribute = getAttributeDefinition(CustomAttribute.class);
        CustomAttributeDefinitionDetailDto dto = new CustomAttributeDefinitionDetailDto();
        dto.setUuid(attribute.getUuid());
        dto.setName(attribute.getName());
        dto.setType(AttributeType.CUSTOM);
        dto.setContentType(attribute.getContentType());
        dto.setContent(attribute.getContent());
        dto.setDescription(attribute.getDescription());
        dto.setEnabled(enabled);
        if (attribute.getProperties() != null) {
            CustomAttributeProperties properties = attribute.getProperties();
            dto.setRequired(properties.isRequired());
            dto.setGroup(properties.getGroup());
            dto.setLabel(properties.getLabel());
            dto.setList(properties.isList());
            dto.setMultiSelect(properties.isMultiSelect());
            dto.setReadOnly(properties.isReadOnly());
            dto.setVisible(properties.isVisible());
        }
        return dto;
    }

    public GlobalMetadataDefinitionDetailDto mapToGlobalMetadataDefinitionDetailDto() {
        MetadataAttribute attribute = getAttributeDefinition(MetadataAttribute.class);
        GlobalMetadataDefinitionDetailDto dto = new GlobalMetadataDefinitionDetailDto();
        dto.setUuid(uuid.toString());
        dto.setName(attribute.getName());
        dto.setType(AttributeType.META);
        dto.setContentType(attribute.getContentType());
        dto.setDescription(attribute.getDescription());
        dto.setEnabled(null);
        if (attribute.getProperties() != null) {
            MetadataAttributeProperties properties = attribute.getProperties();
            dto.setGroup(properties.getGroup());
            dto.setLabel(properties.getLabel());
            dto.setVisible(properties.isVisible());
        }
        return dto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("author", author)
                .append("created", created)
                .append("updated", updated)
                .append("connector", connector)
                .append("connectorUuid", connectorUuid)
                .append("attributeUuid", attributeUuid)
                .append("attributeName", attributeName)
                .append("attributeDefinition", attributeDefinition)
                .append("type", type)
                .append("contentType", contentType)
                .append("uuid", uuid)
                .append("enabled", enabled)
                .toString();
    }
}
