package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.attribute.v2.InfoAttribute;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.UniquelyIdentifiedAndAudited;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "metadata_definition")
public class MetadataDefinition extends UniquelyIdentifiedAndAudited {

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

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("attributeDefinition", attributeDefinition)
                .append("uuid", uuid)
                .toString();
    }
}
