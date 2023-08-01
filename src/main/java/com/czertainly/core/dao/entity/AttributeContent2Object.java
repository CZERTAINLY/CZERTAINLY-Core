package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.auth.Resource;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.UUID;

@Entity
@Table(name = "attribute_content_2_object")
public class AttributeContent2Object extends UniquelyIdentified {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_content_uuid", nullable = false, insertable = false, updatable = false)
    private AttributeContent attributeContent;

    @Column(name = "attribute_content_uuid", nullable = false)
    private UUID attributeContentUuid;

    @Column(name = "object_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private Resource objectType;

    @Column(name = "object_uuid", nullable = false)
    private UUID objectUuid;

    @Column(name = "source_object_type")
    @Enumerated(EnumType.STRING)
    private Resource sourceObjectType;

    @Column(name = "source_object_uuid")
    private UUID sourceObjectUuid;

    @Column(name = "source_object_name")
    private String sourceObjectName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_uuid", insertable = false, updatable = false)
    private Connector connector;

    @Column(name = "connector_uuid")
    private UUID connectorUuid;

    public AttributeContent getAttributeContent() {
        return attributeContent;
    }

    public void setAttributeContent(AttributeContent attributeContent) {
        this.attributeContent = attributeContent;
        this.attributeContentUuid = attributeContent.getUuid();
    }

    public UUID getAttributeContentUuid() {
        return attributeContentUuid;
    }

    public void setAttributeContentUuid(UUID attributeContentUuid) {
        this.attributeContentUuid = attributeContentUuid;
    }

    public Resource getObjectType() {
        return objectType;
    }

    public void setObjectType(Resource objectType) {
        this.objectType = objectType;
    }

    public UUID getObjectUuid() {
        return objectUuid;
    }

    public void setObjectUuid(UUID objectUuid) {
        this.objectUuid = objectUuid;
    }

    public Resource getSourceObjectType() {
        return sourceObjectType;
    }

    public void setSourceObjectType(Resource sourceObjectType) {
        this.sourceObjectType = sourceObjectType;
    }

    public UUID getSourceObjectUuid() {
        return sourceObjectUuid;
    }

    public void setSourceObjectUuid(UUID sourceObjectUuid) {
        this.sourceObjectUuid = sourceObjectUuid;
    }

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

    public String getSourceObjectName() {
        return sourceObjectName;
    }

    public void setSourceObjectName(String sourceObjectName) {
        this.sourceObjectName = sourceObjectName;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("metadataContent", attributeContent)
                .append("metadataContentUuid", attributeContentUuid)
                .append("objectType", objectType)
                .append("objectUuid", objectUuid)
                .append("sourceObjectType", sourceObjectType)
                .append("sourceObjectUuid", sourceObjectUuid)
                .append("connector", connector)
                .append("connectorUuid", connectorUuid)
                .append("sourceObjectName", sourceObjectName)
                .toString();
    }
}
