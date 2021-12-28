package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.connector.EndpointDto;
import com.czertainly.core.util.DtoMapper;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "endpoint")
public class Endpoint extends UniquelyIdentified implements Serializable, DtoMapper<EndpointDto> {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "endpoint_seq")
    @SequenceGenerator(name = "endpoint_seq", sequenceName = "endpoint_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "context")
    private String context;

    @Column(name = "method")
    private String method;

    @Column(name = "required")
    private Boolean required;

    @ManyToOne
    @JoinColumn(name = "function_group_id", nullable = false)
    private FunctionGroup functionGroup;

    @Override
    public EndpointDto mapToDto() {
        EndpointDto dto = new EndpointDto();
        dto.setUuid(this.uuid);
        dto.setName(this.name);
        dto.setContext(this.context);
        dto.setMethod(this.method);
        dto.setRequired(this.required);
        return dto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("id", id)
                .append("uuid", uuid)
                .append("name", name)
                .append("context", context)
                .append("method", method)
                .append("required", required)
                .toString();
    }

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
}
