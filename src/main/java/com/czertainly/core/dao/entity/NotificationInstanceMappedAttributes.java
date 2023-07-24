package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.notification.AttributeMappingDto;
import com.czertainly.core.util.DtoMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "notification_instance_mapped_attributes")
public class NotificationInstanceMappedAttributes extends UniquelyIdentified implements Serializable, DtoMapper<AttributeMappingDto> {
    @Column(name = "notification_instance_ref_uuid")
    private UUID notificationInstanceRefUuid;

    @Column(name = "attribute_definition_uuid")
    private UUID attributeDefinitionUuid;

    @Column(name = "mapping_attribute_uuid")
    private UUID mappingAttributeUuid;

    @Column(name = "mapping_attribute_name")
    private String mappingAttributeName;

    @ManyToOne
    @JoinColumn(name = "notification_instance_ref_uuid", referencedColumnName = "uuid", insertable = false, updatable = false)
    private NotificationInstanceReference notificationInstanceReference;

    @ManyToOne
    @JoinColumn(name = "attribute_definition_uuid", referencedColumnName = "uuid", insertable = false, updatable = false)
    private AttributeDefinition attributeDefinition;

    public UUID getNotificationInstanceRefUuid() {
        return notificationInstanceRefUuid;
    }

    public void setNotificationInstanceRefUuid(UUID notificationInstanceRefUuid) {
        this.notificationInstanceRefUuid = notificationInstanceRefUuid;
    }

    public UUID getAttributeDefinitionUuid() {
        return attributeDefinitionUuid;
    }

    public void setAttributeDefinitionUuid(UUID attributeDefinitionUuid) {
        this.attributeDefinitionUuid = attributeDefinitionUuid;
    }

    public UUID getMappingAttributeUuid() {
        return mappingAttributeUuid;
    }

    public void setMappingAttributeUuid(UUID mappingAttributeUuid) {
        this.mappingAttributeUuid = mappingAttributeUuid;
    }

    public String getMappingAttributeName() {
        return mappingAttributeName;
    }

    public void setMappingAttributeName(String mappingAttributeName) {
        this.mappingAttributeName = mappingAttributeName;
    }

    @Override
    public AttributeMappingDto mapToDto() {
        AttributeMappingDto dto = new AttributeMappingDto();
        dto.setCustomAttributeUuid(this.attributeDefinitionUuid.toString());
        dto.setMappingAttributeUuid(this.mappingAttributeUuid.toString());
        dto.setMappingAttributeName(this.mappingAttributeName);
        return dto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).append("uuid", uuid)
                .append("notificationInstanceRefUuid", notificationInstanceRefUuid)
                .append("attributeDefinitionUuid", attributeDefinitionUuid)
                .append("mappingAttributeUuid", mappingAttributeUuid)
                .append("mappingAttributeName", mappingAttributeName)
                .toString();
    }
}
