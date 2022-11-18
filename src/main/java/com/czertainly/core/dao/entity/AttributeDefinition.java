package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.InfoAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.UniquelyIdentifiedAndAudited;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "attribute_definition")
public class AttributeDefinition extends UniquelyIdentifiedAndAudited {

    @OneToOne
    @JoinColumn(name = "connector_uuid", insertable = false, updatable = false)
    private Connector connector;

    @Column(name = "connector_uuid")
    private UUID connectorUuid;

    @Column(name = "attribute_uuid")
    private UUID attributeUuid;

    @Column(name = "attribute_name")
    private String attributeName;

    @Column(name = "attribute_definition")
    private String attributeDefinition;

    @Column(name = "attribute_type")
    @Enumerated(EnumType.STRING)
    private AttributeType type;

    @Column(name = "attribute_content_type")
    @Enumerated(EnumType.STRING)
    private AttributeContentType contentType;

    public Connector getConnector() {
        return connector;
    }

    public void setConnector(Connector connector) {
        this.connector = connector;
        this.connectorUuid = connector.getUuid();
    }

    public UUID getConnectorUuid() {
        return connectorUuid;
    }

    public void setConnectorUuid(UUID connectorUuid) {
        this.connectorUuid = connectorUuid;
    }

    public UUID getAttributeUuid() {
        return attributeUuid;
    }

    public void setAttributeUuid(UUID attributeUuid) {
        this.attributeUuid = attributeUuid;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    public String getAttributeDefinitionAsString() {
        return attributeDefinition;
    }

    public InfoAttribute getAttributeDefinition() {
        return AttributeDefinitionUtils.deserializeSingleAttribute(attributeDefinition, InfoAttribute.class);
    }

    public void setAttributeDefinition(String attributeDefinition) {
        this.attributeDefinition = attributeDefinition;
    }

    public void setAttributeDefinition(InfoAttribute attributeDefinition) {
        this.attributeDefinition = AttributeDefinitionUtils.serialize(attributeDefinition);
    }

    public AttributeType getType() {
        return type;
    }

    public void setType(AttributeType type) {
        this.type = type;
    }

    public AttributeContentType getContentType() {
        return contentType;
    }

    public void setContentType(AttributeContentType contentType) {
        this.contentType = contentType;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("author", author)
                .append("created", created)
                .append("updated", updated)
                .append("connector", connector)
                .append("connectorUuid", connectorUuid)
                .append("attributeUuid", attributeUuid)
                .append("attributeName", attributeName)
                .append("attributeDefinition", attributeDefinition)
                .append("type", type)
                .append("contentType", contentType)
                .append("uuid", uuid)
                .toString();
    }
}
