package com.czertainly.core.attribute.engine.records;

import com.czertainly.api.model.common.attribute.common.BaseAttributeContent;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;

import java.util.UUID;

public record ObjectAttributeContent(
    UUID uuid,
    String name,
    String label,
    AttributeType type,
    AttributeContentType contentType,
    BaseAttributeContent contentItem
)
{}
