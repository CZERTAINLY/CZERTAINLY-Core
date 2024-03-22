package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.auth.Resource;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "attribute_content_2_object")
public class AttributeContent2Object extends UniquelyIdentified {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_uuid", insertable = false, updatable = false)
    private Connector connector;

    @Column(name = "connector_uuid")
    private UUID connectorUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_content_item_uuid", nullable = false, insertable = false, updatable = false)
    private AttributeContentItem attributeContentItem;

    @Column(name = "attribute_content_item_uuid", nullable = false)
    private UUID attributeContentItemUuid;

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

    @Column(name = "item_order")
    private int order;

    public void setConnector(Connector connector) {
        this.connector = connector;
        if (connector != null) this.connectorUuid = connector.getUuid();
    }

    public void setAttributeContentItem(AttributeContentItem attributeContentItem) {
        this.attributeContentItem = attributeContentItem;
        this.attributeContentItemUuid = attributeContentItem.getUuid();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("uuid", uuid)
                .append("connectorUuid", connectorUuid)
                .append("attributeContentItemUuid", attributeContentItemUuid)
                .append("objectType", objectType)
                .append("objectUuid", objectUuid)
                .append("sourceObjectType", sourceObjectType)
                .append("sourceObjectUuid", sourceObjectUuid)
                .append("sourceObjectName", sourceObjectName)
                .append("order", order)
                .toString();
    }
}
