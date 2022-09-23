package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.entity.EntityInstanceDto;
import com.czertainly.core.util.DtoMapper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "entity_instance_reference")
public class EntityInstanceReference extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<EntityInstanceDto> {

    @Column(name = "entity_instance_uuid")
    private String entityInstanceUuid;

    @Column(name = "name")
    private String name;

    @Column(name = "status")
    private String status;

    @Column(name = "kind")
    private String kind;

    @ManyToOne
    @JoinColumn(name = "connector_uuid", insertable = false, updatable = false)
    private Connector connector;

    @Column(name = "connector_uuid")
    private UUID connectorUuid;

    @Column(name="connector_name")
    private String connectorName;

    @OneToMany(mappedBy = "entityInstanceReference")
    @JsonIgnore
    private Set<Location> locations = new HashSet<>();

    public String getEntityInstanceUuid() {
        return entityInstanceUuid;
    }

    public void setEntityInstanceUuid(String entityInstanceUuid) {
        this.entityInstanceUuid = entityInstanceUuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Connector getConnector() {
        return connector;
    }

    public void setConnector(Connector connector) {
        this.connector = connector;
        if(connector != null) this.connectorUuid = connector.getUuid();
    }

    public Set<Location> getLocations() {
        return locations;
    }

    public void setLocations(Set<Location> locations) {
        this.locations = locations;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getConnectorName() { return connectorName; }

    public void setConnectorName(String connectorName) { this.connectorName = connectorName; }

    public UUID getConnectorUuid() {
        return connectorUuid;
    }

    public void setConnectorUuid(UUID connectorUuid) {
        this.connectorUuid = connectorUuid;
    }

    public EntityInstanceDto mapToDto() {
        EntityInstanceDto dto = new EntityInstanceDto();
        dto.setUuid(this.uuid.toString());
        dto.setName(this.name);
        dto.setStatus(this.status);
        dto.setKind(kind);
        dto.setConnectorName(this.connectorName);
        if (this.connector != null) {
            dto.setConnectorUuid(this.connector.getUuid().toString());
        }
        return dto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("uuid", uuid)
                .append("entityInstanceUuid", entityInstanceUuid)
                .append("name", name)
                .append("status", status)
                .append("type", kind)
                .append("connectorName", connectorName)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityInstanceReference that = (EntityInstanceReference) o;
        return new EqualsBuilder().append(uuid, that.uuid).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(uuid).toHashCode();
    }
}
