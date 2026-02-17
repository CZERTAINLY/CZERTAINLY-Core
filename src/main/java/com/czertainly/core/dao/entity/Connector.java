package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.core.connector.AuthType;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.FunctionGroupDto;
import com.czertainly.api.model.core.connector.v2.ConnectorDetailDto;
import com.czertainly.api.model.core.connector.v2.ConnectorDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.MetaDefinitions;
import com.czertainly.core.util.ObjectAccessControlMapper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "connector")
public class Connector extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<ConnectorDto>, ObjectAccessControlMapper<NameAndUuidDto> {

    @Serial
    private static final long serialVersionUID = -4057975339123024975L;

    @Column(name = "name")
    private String name;

    @Column(name = "version")
    private String version;

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

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "connector")
    @ToString.Exclude
    @JsonManagedReference
    private Set<Connector2FunctionGroup> functionGroups = new HashSet<>();

    @ToString.Exclude
    @OneToMany(mappedBy = "connector", fetch = FetchType.LAZY)
    private Set<ConnectorInterfaceEntity> interfaces = new HashSet<>();

    @OneToMany(mappedBy = "connectorUuid", fetch = FetchType.LAZY)
    @JsonIgnore
    @ToString.Exclude
    private Set<Credential> credentials = new HashSet<>();

    @OneToMany(mappedBy = "connector", fetch = FetchType.LAZY)
    @JsonIgnore
    @ToString.Exclude
    private Set<AuthorityInstanceReference> authorityInstanceReferences = new HashSet<>();

    @OneToMany(mappedBy = "connector", fetch = FetchType.LAZY)
    @JsonIgnore
    @ToString.Exclude
    private Set<EntityInstanceReference> entityInstanceReferences = new HashSet<>();

    @OneToMany(mappedBy = "connectorUuid", fetch = FetchType.LAZY)
    @JsonIgnore
    @ToString.Exclude
    private Set<TokenInstanceReference> tokenInstanceReferences = new HashSet<>();

    @Override
    public ConnectorDto mapToDto() {
        ConnectorDto dto = new ConnectorDto();
        dto.setUuid(this.uuid.toString());
        dto.setName(this.name);
        dto.setVersion(this.version);
        dto.setUrl(this.url);
        dto.setStatus(this.status);
        return dto;
    }

    public ConnectorDetailDto mapToDetailDto() {
        ConnectorDetailDto dto = new ConnectorDetailDto();
        dto.setUuid(this.uuid.toString());
        dto.setName(this.name);
        dto.setVersion(this.version);
        dto.setUrl(this.url);
        dto.setStatus(this.status);
        dto.setAuthType(authType);
        dto.setAuthAttributes(AttributeEngine.getResponseAttributesFromBaseAttributes(AttributeDefinitionUtils.deserialize(this.authAttributes, BaseAttribute.class)));
        dto.setInterfaces(this.interfaces.stream().map(ConnectorInterfaceEntity::mapToDto).toList());
        dto.setFunctionGroups(this.functionGroups.stream().map(f -> {
            FunctionGroupDto functionGroupDto = f.getFunctionGroup().mapToDto();
            functionGroupDto.setKinds(MetaDefinitions.deserializeArrayString(f.getKinds()));
            return functionGroupDto;
        }).toList());

        return dto;
    }

    public com.czertainly.api.model.core.connector.ConnectorDto mapToDtoV1() {
        var dto = new com.czertainly.api.model.core.connector.ConnectorDto();
        dto.setUuid(this.uuid.toString());
        dto.setName(this.name);
        dto.setUrl(this.url);
        dto.setAuthType(authType);
        dto.setAuthAttributes(AttributeEngine.getResponseAttributesFromBaseAttributes(AttributeDefinitionUtils.deserialize(this.authAttributes, BaseAttribute.class)));
        dto.setStatus(this.status);
        dto.setFunctionGroups(this.functionGroups.stream().map(f -> {
            FunctionGroupDto functionGroupDto = f.getFunctionGroup().mapToDto();
            functionGroupDto.setKinds(MetaDefinitions.deserializeArrayString(f.getKinds()));
            return functionGroupDto;
        }).toList());
        return dto;
    }

    @Override
    public NameAndUuidDto mapToAccessControlObjects() {
        return new NameAndUuidDto(uuid.toString(), name);
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Connector connector = (Connector) o;
        return getUuid() != null && Objects.equals(getUuid(), connector.getUuid());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
