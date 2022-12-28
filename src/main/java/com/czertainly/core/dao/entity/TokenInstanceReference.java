package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.connector.cryptography.enums.TokenInstanceStatus;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceDetailDto;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceDto;
import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.ObjectAccessControlMapper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "token_instance_reference")
public class TokenInstanceReference extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<TokenInstanceDto>, ObjectAccessControlMapper<NameAndUuidDto> {

    @Column(name = "token_instance_uuid")
    private String tokenInstanceUuid;

    @Column(name = "name")
    private String name;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private TokenInstanceStatus status;

    @Column(name = "kind")
    private String kind;

    @ManyToOne
    @JoinColumn(name = "connector_uuid", insertable = false, updatable = false)
    private Connector connector;

    @Column(name = "connector_uuid")
    private UUID connectorUuid;

    @Column(name="connector_name")
    private String connectorName;

    @OneToMany(mappedBy = "tokenInstanceReference")
    @JsonIgnore
    private Set<TokenProfile> tokenProfiles = new HashSet<>();

    public String getTokenInstanceUuid() {
        return tokenInstanceUuid;
    }

    public void setTokenInstanceUuid(String tokenInstanceUuid) {
        this.tokenInstanceUuid = tokenInstanceUuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TokenInstanceStatus getStatus() {
        return status;
    }

    public void setStatus(TokenInstanceStatus status) {
        this.status = status;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public Connector getConnector() {
        return connector;
    }

    public void setConnector(Connector connector) {
        this.connector = connector;
        if (connector != null) this.connectorUuid = connector.getUuid();
    }

    public UUID getConnectorUuid() {
        return connectorUuid;
    }

    public void setConnectorUuid(UUID connectorUuid) {
        this.connectorUuid = connectorUuid;
    }

    public String getConnectorName() {
        return connectorName;
    }

    public void setConnectorName(String connectorName) {
        this.connectorName = connectorName;
    }

    public Set<TokenProfile> getTokenProfiles() {
        return tokenProfiles;
    }

    public void setTokenProfiles(Set<TokenProfile> tokenProfiles) {
        this.tokenProfiles = tokenProfiles;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("tokenInstanceUuid", tokenInstanceUuid)
                .append("name", name)
                .append("status", status)
                .append("kind", kind)
                .append("connector", connector)
                .append("connectorUuid", connectorUuid)
                .append("connectorName", connectorName)
                .append("tokenProfiles", tokenProfiles)
                .append("uuid", uuid)
                .toString();
    }

    @Override
    public TokenInstanceDto mapToDto() {
        TokenInstanceDto dto = new TokenInstanceDto();
        dto.setName(name);
        dto.setStatus(status);
        dto.setUuid(uuid.toString());
        dto.setTokenProfiles(tokenProfiles.size());
        dto.setConnectorName(connectorName);
        dto.setConnectorUuid(connectorUuid.toString());
        return dto;
    }

    public TokenInstanceDetailDto mapToDetailDto() {
        TokenInstanceDetailDto dto = new TokenInstanceDetailDto();
        dto.setName(name);
        dto.setStatus(status);
        dto.setUuid(uuid.toString());
        dto.setTokenProfiles(tokenProfiles.size());
        dto.setConnectorName(connectorName);
        dto.setConnectorUuid(connectorUuid.toString());
        //Attribute should be fetched from the connector and set to the DTO
        // Custom Attributes and the Metadata should be set in the service
        return dto;
    }

    @Override
    public NameAndUuidDto mapToAccessControlObjects() {
        return new NameAndUuidDto(uuid.toString(), name);
    }
}
