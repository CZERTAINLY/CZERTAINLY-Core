package com.czertainly.core.dao.entity;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.czertainly.core.util.DtoMapper;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.czertainly.api.model.connector.FunctionGroupCode;
import com.czertainly.api.model.connector.FunctionGroupDto;

@Entity
@Table(name = "function_group")
public class FunctionGroup extends UniquelyIdentified implements Serializable, DtoMapper<FunctionGroupDto> {
    private static final long serialVersionUID = 463898767718879135L;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "function_group_seq")
    @SequenceGenerator(name = "function_group_seq", sequenceName = "function_group_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "code")
    @Enumerated(EnumType.STRING)
    private FunctionGroupCode code;

    @OneToMany(mappedBy = "functionGroup")
    private Set<Endpoint> endpoints = new HashSet<>();

    @OneToMany(mappedBy = "functionGroup")
    private Set<Connector2FunctionGroup> connectors = new HashSet<>();

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
        dto.setUuid(this.uuid);
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
                .append("id", id)
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

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
