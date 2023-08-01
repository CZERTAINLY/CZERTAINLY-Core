package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.connector.FunctionGroupDto;
import com.czertainly.core.util.DtoMapper;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "function_group")
public class FunctionGroup extends UniquelyIdentified implements Serializable, DtoMapper<FunctionGroupDto> {
    private static final long serialVersionUID = 463898767718879135L;

    @Column(name = "name")
    private String name;

    @Column(name = "code")
    @Enumerated(EnumType.STRING)
    private FunctionGroupCode code;

    /**
     * The endpoints that belong to this function group.
     * We need EAGER fetching here because we need to know the endpoints when we create the function group DTO.
     */
    @OneToMany(fetch = FetchType.EAGER, mappedBy = "functionGroup")
    private Set<Endpoint> endpoints = new HashSet<>();

    /**
     * The connectors that belong to this function group.
     * We need EAGER fetching here because we need to know the connectors when we create the function group DTO.
     */
    @OneToMany(fetch = FetchType.EAGER, mappedBy = "functionGroup")
    private Set<Connector2FunctionGroup> connectors = new HashSet<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public FunctionGroupCode getCode() {
        return code;
    }

    public void setCode(FunctionGroupCode code) {
        this.code = code;
    }

    public Set<Endpoint> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(Set<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public FunctionGroupDto mapToDto() {
        FunctionGroupDto dto = new FunctionGroupDto();
        if(this.uuid != null) dto.setUuid(this.uuid.toString());
        dto.setName(this.name);
        dto.setFunctionGroupCode(this.code);
        dto.setEndPoints(this.endpoints.stream()
                .map(Endpoint::mapToDto)
                .collect(Collectors.toList()));

        return dto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("uuid", uuid)
                .append("name", name)
                .append("code", code)
                .append("endpoints", endpoints)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FunctionGroup that = (FunctionGroup) o;

        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
