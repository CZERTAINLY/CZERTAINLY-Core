package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.authority.AuthorityInstanceDto;
import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.ObjectAccessControlMapper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "authority_instance_reference")
public class AuthorityInstanceReference extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<AuthorityInstanceDto>, ObjectAccessControlMapper<NameAndUuidDto> {
    private static final long serialVersionUID = -2377655450967447704L;

    @Column(name = "authority_instance_uuid")
    private String authorityInstanceUuid;

    @Column(name = "name")
    private String name;

    @Column(name = "status")
    private String status;

    @Column(name = "kind")
    private String kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_uuid", insertable = false, updatable = false)
    private Connector connector;

    @Column(name = "connector_uuid")
    private UUID connectorUuid;

    @Column(name="connector_name")
    private String connectorName;

    @OneToMany(mappedBy = "authorityInstanceReference")
    @JsonIgnore
    private Set<RaProfile> raProfiles = new HashSet<>();

    public String getAuthorityInstanceUuid() {
        return authorityInstanceUuid;
    }

    public void setAuthorityInstanceUuid(String authorityInstanceUuid) {
        this.authorityInstanceUuid = authorityInstanceUuid;
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
        else this.connectorUuid = null;
    }

    public Set<RaProfile> getRaProfiles() {
        return raProfiles;
    }

    public void setRaProfiles(Set<RaProfile> raProfiles) {
        this.raProfiles = raProfiles;
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

    @Override
    public AuthorityInstanceDto mapToDto() {
        AuthorityInstanceDto dto = new AuthorityInstanceDto();
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
    public NameAndUuidDto mapToAccessControlObjects() {
        return new NameAndUuidDto(uuid.toString(), name);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("uuid", uuid)
                .append("authorityInstanceUuid", authorityInstanceUuid)
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
        AuthorityInstanceReference that = (AuthorityInstanceReference) o;
        return new EqualsBuilder().append(uuid, that.uuid).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(uuid).toHashCode();
    }
}
