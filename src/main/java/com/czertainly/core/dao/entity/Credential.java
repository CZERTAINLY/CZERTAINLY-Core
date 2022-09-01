package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.credential.CredentialDto;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.DtoMapper;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "credential")
public class Credential extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<CredentialDto> {

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

    @Column(name = "connector_uuid")
    private UUID connectorUuid;

    @Column(name="connector_name")
    private String connectorName;

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

    public UUID getConnectorUuid() {
        return connectorUuid;
    }

    public void setConnectorUuid(UUID connectorUuid) {
        this.connectorUuid = connectorUuid;
    }

    public String getConnectorName() { return connectorName; }

    public void setConnectorName(String connectorName) { this.connectorName = connectorName; }

    public CredentialDto mapToDtoSimple() {
        CredentialDto dto = new CredentialDto();
        dto.setUuid(this.uuid.toString());
        dto.setName(this.name);
        dto.setKind(this.kind);
        dto.setEnabled(this.enabled);
        dto.setConnectorName(this.connectorName);
        dto.setConnectorUuid(this.connectorUuid.toString());

        return dto;
    }

    @Override
    public CredentialDto mapToDto() {
        CredentialDto dto = new CredentialDto();
        dto.setUuid(this.uuid.toString());
        dto.setName(this.name);
        dto.setKind(this.kind);
        dto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(AttributeDefinitionUtils.deserialize(this.attributes)));
        dto.setEnabled(this.enabled);
        dto.setConnectorName(this.connectorName);
        dto.setConnectorUuid(this.connectorUuid.toString());

        return dto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("name", name)
                .append("type", kind)
                .append("uuid", uuid)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Credential that = (Credential) o;
        return new EqualsBuilder().append(uuid, that.uuid).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(uuid).toHashCode();
    }
}
