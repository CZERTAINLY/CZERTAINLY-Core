package com.czertainly.core.dao.entity;

import com.czertainly.core.util.DtoMapper;
import com.czertainly.api.model.credential.CredentialDto;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "credential")
public class Credential extends Audited implements Serializable, DtoMapper<CredentialDto> {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "credential_seq")
    @SequenceGenerator(name = "credential_seq", sequenceName = "credential_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "kind")
    private String kind;

    //    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "attributes")
    private String attributes;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name="connector_name")
    private String connectorName;

    @ManyToOne
    @JoinColumn(name = "connector_id")
    private Connector connector;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getAttributes() {
        return attributes;
    }

    public void setAttributes(String attributes) {
        this.attributes = attributes;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Connector getConnector() {
        return connector;
    }

    public void setConnector(Connector connector) {
        this.connector = connector;
    }

    public String getConnectorName() { return connectorName; }

    public void setConnectorName(String connectorName) { this.connectorName = connectorName; }

    public CredentialDto mapToDtoSimple() {
        CredentialDto dto = new CredentialDto();
        dto.setUuid(this.uuid);
        dto.setName(this.name);
        dto.setKind(this.kind);
        dto.setEnabled(this.enabled);
        dto.setConnectorName(this.connectorName);
        if (this.connector != null) {
            dto.setConnectorUuid(this.connector.getUuid());
        }
        return dto;
    }

    @Override
    public CredentialDto mapToDto() {
        CredentialDto dto = new CredentialDto();
        dto.setUuid(this.uuid);
        dto.setName(this.name);
        dto.setKind(this.kind);
        dto.setAttributes(AttributeDefinitionUtils.deserialize(this.attributes));
        dto.setEnabled(this.enabled);
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
                .append("name", name)
                .append("type", kind)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Credential that = (Credential) o;
        return new EqualsBuilder().append(id, that.id).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(id).toHashCode();
    }
}
