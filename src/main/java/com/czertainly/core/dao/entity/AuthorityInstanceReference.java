package com.czertainly.core.dao.entity;

import com.czertainly.core.util.DtoMapper;
import com.czertainly.api.model.ca.AuthorityInstanceDto;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "authority_instance_reference")
public class AuthorityInstanceReference extends Audited implements Serializable, DtoMapper<AuthorityInstanceDto> {
    private static final long serialVersionUID = -2377655450967447704L;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "authority_instance_reference_seq")
    @SequenceGenerator(name = "authority_instance_reference_seq", sequenceName = "authority_instance_reference_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "authority_instance_uuid")
    private String authorityInstanceUuid;

    @Column(name = "name")
    private String name;

    @Column(name = "status")
    private String status;

    @Column(name = "type")
    private String authorityType;

    @ManyToOne
    @JoinColumn(name = "connector_id")
    private Connector connector;

    @Column(name="connector_name")
    private String connectorName;

    @OneToMany(mappedBy = "authorityInstanceReference")
    @JsonIgnore
    private Set<RaProfile> raProfiles = new HashSet<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
    }

    public Set<RaProfile> getRaProfiles() {
        return raProfiles;
    }

    public void setRaProfiles(Set<RaProfile> raProfiles) {
        this.raProfiles = raProfiles;
    }

    public String getAuthorityType() {
        return authorityType;
    }

    public void setAuthorityType(String authorityType) {
        this.authorityType = authorityType;
    }

    public String getConnectorName() { return connectorName; }

    public void setConnectorName(String connectorName) { this.connectorName = connectorName; }

    public AuthorityInstanceDto mapToDto() {
        AuthorityInstanceDto dto = new AuthorityInstanceDto();
        dto.setUuid(this.uuid);
        dto.setName(this.name);
        dto.setStatus(this.status);
        dto.setAuthorityType(authorityType);
        dto.setConnectorName(this.connectorName);
        if (this.connector != null) {
            dto.setConnectorUuid(this.connector.getUuid());
        }
        return dto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("id", id)
                .append("uuid", uuid)
                .append("authorityInstanceUuid", authorityInstanceUuid)
                .append("name", name)
                .append("status", status)
                .append("type", authorityType)
                .append("connectorName", connectorName)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthorityInstanceReference that = (AuthorityInstanceReference) o;
        return new EqualsBuilder().append(id, that.id).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(id).toHashCode();
    }
}
