package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.UUID;

@Entity
@Table(name = "attribute_relation")
public class AttributeRelation extends UniquelyIdentified {

    @OneToOne
    @JoinColumn(name = "attribute_definition_uuid", insertable = false, updatable = false)
    private AttributeDefinition attributeDefinition;

    @Column(name = "attribute_definition_uuid")
    private UUID attributeDefinitionUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource")
    private Resource resource;

    @Enumerated(EnumType.STRING)
    @Column(name = "function_group_code")
    private FunctionGroupCode functionGroupCode;

    @Column(name = "kind")
    private String kind;

    public AttributeDefinition getAttributeDefinition() {
        return attributeDefinition;
    }

    public void setAttributeDefinition(AttributeDefinition attributeDefinition) {
        this.attributeDefinition = attributeDefinition;
        this.attributeDefinitionUuid = attributeDefinition.getUuid();
    }

    public UUID getAttributeDefinitionUuid() {
        return attributeDefinitionUuid;
    }

    public void setAttributeDefinitionUuid(UUID attributeDefinitionUuid) {
        this.attributeDefinitionUuid = attributeDefinitionUuid;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public FunctionGroupCode getFunctionGroupCode() {
        return functionGroupCode;
    }

    public void setFunctionGroupCode(FunctionGroupCode functionGroupCode) {
        this.functionGroupCode = functionGroupCode;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("attributeDefinition", attributeDefinition)
                .append("attributeDefinitionUuid", attributeDefinitionUuid)
                .append("resource", resource)
                .append("functionGroupCode", functionGroupCode)
                .append("kind", kind)
                .append("uuid", uuid)
                .toString();
    }
}
