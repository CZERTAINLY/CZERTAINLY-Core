package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.connector.EndpointDto;
import com.czertainly.core.util.DtoMapper;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "endpoint")
public class Endpoint extends UniquelyIdentified implements Serializable, DtoMapper<EndpointDto> {

    @Column(name = "name")
    private String name;

    @Column(name = "context")
    private String context;

    @Column(name = "method")
    private String method;

    @Column(name = "required")
    private Boolean required;

    @ManyToOne
    @JoinColumn(name = "function_group_uuid", nullable = false, insertable = false, updatable = false)
    private FunctionGroup functionGroup;

    @Column(name = "function_group_uuid", nullable = false)
    private UUID functionGroupUuid;

    @Override
    public EndpointDto mapToDto() {
        EndpointDto dto = new EndpointDto();
        dto.setUuid(this.uuid.toString());
        dto.setName(this.name);
        dto.setContext(this.context);
        dto.setMethod(this.method);
        dto.setRequired(this.required);
        return dto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("uuid", uuid)
                .append("name", name)
                .append("context", context)
                .append("method", method)
                .append("required", required)
                .toString();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Boolean isRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public UUID getFunctionGroupUuid() {
        return functionGroupUuid;
    }

    public void setFunctionGroupUuid(UUID functionGroupUuid) {
        this.functionGroupUuid = functionGroupUuid;
    }

    public void setFunctionGroupUuid(String functionGroupUuid) {
        this.functionGroupUuid = UUID.fromString(functionGroupUuid);
    }
}
