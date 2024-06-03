package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.notification.NotificationInstanceDto;
import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.ObjectAccessControlMapper;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "notification_instance_reference")
public class NotificationInstanceReference extends UniquelyIdentified implements Serializable, DtoMapper<NotificationInstanceDto>, ObjectAccessControlMapper<NameAndUuidDto> {
    @Column(name = "notification_instance_uuid")
    private UUID notificationInstanceUuid;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "kind")
    private String kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_uuid", insertable = false, updatable = false)
    private Connector connector;

    @Column(name = "connector_uuid")
    private UUID connectorUuid;

    @Column(name = "connector_name")
    private String connectorName;

    @OneToMany(mappedBy = "notificationInstanceReference", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<NotificationInstanceMappedAttributes> mappedAttributes;

    public UUID getNotificationInstanceUuid() {
        return notificationInstanceUuid;
    }

    public void setNotificationInstanceUuid(UUID notificationInstanceUuid) {
        this.notificationInstanceUuid = notificationInstanceUuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Connector getConnector() {
        return connector;
    }

    public void setConnector(Connector connector) {
        this.connector = connector;
        if (connector != null) this.connectorUuid = connector.getUuid();
        else this.connectorUuid = null;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getConnectorName() {
        return connectorName;
    }

    public void setConnectorName(String connectorName) {
        this.connectorName = connectorName;
    }

    public UUID getConnectorUuid() {
        return connectorUuid;
    }

    public void setConnectorUuid(UUID connectorUuid) {
        this.connectorUuid = connectorUuid;
    }

    public List<NotificationInstanceMappedAttributes> getMappedAttributes() {
        return mappedAttributes;
    }

    public void setMappedAttributes(List<NotificationInstanceMappedAttributes> mappedAttributes) {
        this.mappedAttributes = mappedAttributes;
    }

    @Override
    public NotificationInstanceDto mapToDto() {
        NotificationInstanceDto dto = new NotificationInstanceDto();
        dto.setUuid(this.uuid.toString());
        dto.setName(this.name);
        dto.setDescription(this.description);
        dto.setKind(kind);
        dto.setConnectorName(this.connectorName);
        if (this.connector != null) {
            dto.setConnectorUuid(this.connector.getUuid().toString());
        }
        return dto;
    }

    @Override
    public NameAndUuidDto mapToAccessControlObjects() {
        return new NameAndUuidDto(uuid.toString(), name);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).append("uuid", uuid)
                .append("notificationInstanceUuid", notificationInstanceUuid)
                .append("name", name)
                .append("description", description)
                .append("kind", kind)
                .append("connectorName", connectorName)
                .toString();
    }
}
