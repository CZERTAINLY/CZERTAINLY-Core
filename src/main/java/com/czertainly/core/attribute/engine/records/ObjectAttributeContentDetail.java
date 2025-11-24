package com.czertainly.core.attribute.engine.records;

import com.czertainly.api.model.common.attribute.common.BaseAttributeContent;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.core.auth.Resource;

import java.util.UUID;

public record ObjectAttributeContentDetail(
    UUID uuid,
    String name,
    String label,
    AttributeType type,
    AttributeContentType contentType,
    BaseAttributeContent contentItem,
    UUID connectorUuid,
    String connectorName,
    Resource sourceObjectType,
    UUID sourceObjectUuid,
    String sourceObjectName
)
{}
