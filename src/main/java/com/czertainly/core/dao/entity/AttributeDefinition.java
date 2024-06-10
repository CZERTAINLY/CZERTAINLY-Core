package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.attribute.AttributeDefinitionDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeDefinitionDetailDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeDefinitionDto;
import com.czertainly.api.model.client.attribute.metadata.GlobalMetadataDefinitionDetailDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.*;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.properties.CustomAttributeProperties;
import com.czertainly.api.model.common.attribute.v2.properties.MetadataAttributeProperties;
import com.czertainly.core.util.ObjectAccessControlMapper;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "attribute_definition")
@EntityListeners(AuditingEntityListener.class)
public class AttributeDefinition extends UniquelyIdentified implements ObjectAccessControlMapper<NameAndUuidDto> {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_uuid", insertable = false, updatable = false)
    private Connector connector;

    @Column(name = "connector_uuid")
    private UUID connectorUuid;

    @Column(name = "attribute_uuid", nullable = false)
    private UUID attributeUuid;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private AttributeType type;

    @Column(name = "content_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private AttributeContentType contentType;

    @Column(name = "label", nullable = false)
    private String label;

    @Getter(AccessLevel.NONE)
    @Column(name = "required")
    private Boolean required;

    @Getter(AccessLevel.NONE)
    @Column(name = "read_only")
    private Boolean readOnly;

    @Column(name = "definition", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private BaseAttribute definition;

    @Getter(AccessLevel.NONE)
    @Column(name = "enabled")
    private Boolean enabled;

    @Getter(AccessLevel.NONE)
    @Column(name = "global")
    private Boolean global;

    @Column(name = "operation")
    private String operation;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    protected LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
    protected LocalDateTime updatedAt;

    @OneToMany(mappedBy = "attributeDefinition", fetch = FetchType.LAZY)
    private List<AttributeContentItem> contentItems;

    @JsonBackReference
    @OneToMany(mappedBy = "attributeDefinition", fetch = FetchType.LAZY)
    private List<AttributeRelation> relations = new ArrayList<>();

    public void setConnector(Connector connector) {
        this.connector = connector;
        if (connector != null) this.connectorUuid = connector.getUuid();
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public Boolean isGlobal() {
        return global;
    }

    public Boolean isRequired() {
        return required;
    }

    public Boolean isReadOnly() {
        return readOnly;
    }


    @Override
    public NameAndUuidDto mapToAccessControlObjects() {
        return new NameAndUuidDto(uuid.toString(), name);
    }

    public CustomAttributeDefinitionDto mapToCustomAttributeDefinitionDto() {
        CustomAttributeDefinitionDto dto = new CustomAttributeDefinitionDto();
        CustomAttribute attribute = (CustomAttribute) this.definition;
        dto.setUuid(attribute.getUuid());
        dto.setName(attribute.getName());
        dto.setDescription(attribute.getDescription());
        dto.setContentType(attribute.getContentType());
        dto.setEnabled(enabled);
        dto.setResources(this.relations.stream().map(AttributeRelation::getResource).toList());
        return dto;
    }

    public AttributeDefinitionDto mapToGlobalMetadataDefinitionDto() {
        AttributeDefinitionDto dto = new AttributeDefinitionDto();
        MetadataAttribute attribute = (MetadataAttribute) this.definition;
        dto.setUuid(uuid.toString());
        dto.setName(attribute.getName());
        dto.setContentType(attribute.getContentType());
        dto.setDescription(attribute.getDescription());
        return dto;
    }

    public CustomAttributeDefinitionDetailDto mapToCustomAttributeDefinitionDetailDto() {
        CustomAttribute attribute = (CustomAttribute) this.definition;
        CustomAttributeDefinitionDetailDto dto = new CustomAttributeDefinitionDetailDto();
        dto.setUuid(attribute.getUuid());
        dto.setName(attribute.getName());
        dto.setType(AttributeType.CUSTOM);
        dto.setContentType(attribute.getContentType());
        dto.setContent(attribute.getContent());
        dto.setDescription(attribute.getDescription());
        dto.setEnabled(enabled);
        dto.setResources(this.relations.stream().map(AttributeRelation::getResource).toList());
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
        MetadataAttribute attribute = (MetadataAttribute) this.definition;
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
                .append("uuid", uuid)
                .append("connector", connector)
                .append("connectorUuid", connectorUuid)
                .append("attributeUuid", attributeUuid)
                .append("name", name)
                .append("type", type)
                .append("contentType", contentType)
                .append("label", label)
                .append("required", required)
                .append("readOnly", readOnly)
                .append("enabled", enabled)
                .append("global", global)
                .append("operation", operation)
                .append("definition", definition)
                .toString();
    }
}
