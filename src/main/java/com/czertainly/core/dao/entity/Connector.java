package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.connector.AuthType;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.FunctionGroupDto;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.MetaDefinitions;
import com.czertainly.core.util.ObjectAccessControlMapper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "connector")
public class Connector extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<ConnectorDto>, ObjectAccessControlMapper<NameAndUuidDto> {
    private static final long serialVersionUID = -4057975339123024975L;

    @Column(name = "name")
    private String name;

    @Column(name = "url")
    private String url;

    @Column(name = "auth_type")
    @Enumerated(EnumType.STRING)
    private AuthType authType;
    
    @Column(name = "auth_attributes")
    private String authAttributes;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ConnectorStatus status;

    @OneToMany(fetch = FetchType.EAGER, mappedBy = "connector")
    private Set<Connector2FunctionGroup> functionGroups = new HashSet<>();

    @OneToMany(mappedBy = "connectorUuid")
    @JsonIgnore
    private Set<Credential> credentials = new HashSet<>();

    @OneToMany(mappedBy = "connector")
    @JsonIgnore
    private Set<AuthorityInstanceReference> authorityInstanceReferences = new HashSet<>();

    @OneToMany(mappedBy = "connector")
    @JsonIgnore
    private Set<EntityInstanceReference> entityInstanceReferences = new HashSet<>();

    @OneToMany(mappedBy = "connectorUuid")
    @JsonIgnore
    private Set<TokenInstanceReference> tokenInstanceReferences = new HashSet<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public AuthType getAuthType() {
        return authType;
    }

    public void setAuthType(AuthType authType) {
        this.authType = authType;
    }

    public String getAuthAttributes() {
        return authAttributes;
    }

    public void setAuthAttributes(String authAttributes) {
        this.authAttributes = authAttributes;
    }

    public ConnectorStatus getStatus() {
        return status;
    }

    public void setStatus(ConnectorStatus status) {
        this.status = status;
    }

    public Set<Connector2FunctionGroup> getFunctionGroups() {
        return functionGroups;
    }

    public void setFunctionGroups(Set<Connector2FunctionGroup> functionGroups) {
        this.functionGroups = functionGroups;
    }

    public Set<Credential> getCredentials() {
        return credentials;
    }

    public void setCredentials(Set<Credential> credentials) {
        this.credentials = credentials;
    }

    public Set<AuthorityInstanceReference> getAuthorityInstanceReferences() {
        return authorityInstanceReferences;
    }

    public void setAuthorityInstanceReferences(Set<AuthorityInstanceReference> authorityInstanceReferences) {
        this.authorityInstanceReferences = authorityInstanceReferences;
    }

    public Set<EntityInstanceReference> getEntityInstanceReferences() {
        return entityInstanceReferences;
    }

    public Set<TokenInstanceReference> getTokenInstanceReferences() {
        return tokenInstanceReferences;
    }

    @Override
    public ConnectorDto mapToDto() {
        ConnectorDto dto = new ConnectorDto();
        dto.setUuid(this.uuid.toString());
        dto.setName(this.name);
        dto.setUrl(this.url);
        dto.setAuthType(authType);
        dto.setAuthAttributes(AttributeDefinitionUtils.getResponseAttributes(AttributeDefinitionUtils.deserialize(this.authAttributes, BaseAttribute.class)));
        dto.setStatus(this.status);
        dto.setFunctionGroups(this.functionGroups.stream().map(f -> {
            FunctionGroupDto functionGroupDto = f.getFunctionGroup().mapToDto();
            functionGroupDto.setKinds(MetaDefinitions.deserializeArrayString(f.getKinds()));
            return functionGroupDto;
        }).collect(Collectors.toList()));
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
                .append("name", name)
                .append("authType", authType)
                .append("authAttributes", authAttributes)
                .append("url", url)
                .append("status", status)
                .append("functionGroups", functionGroups)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Connector that = (Connector) o;
        return new EqualsBuilder().append(uuid, that.uuid).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(uuid).toHashCode();
    }
}
