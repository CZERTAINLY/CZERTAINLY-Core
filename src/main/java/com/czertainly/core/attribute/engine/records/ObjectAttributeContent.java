package com.czertainly.core.attribute.engine.records;

import com.czertainly.api.model.common.attribute.common.AttributeContent;
import com.czertainly.api.model.common.attribute.common.AttributeVersion;
import com.czertainly.api.model.common.attribute.common.AttributeType;
import com.czertainly.api.model.common.attribute.common.content.AttributeContentType;

import java.util.UUID;

public record ObjectAttributeContent(
    UUID uuid,
    String name,
    String label,
    AttributeType type,
    AttributeContentType contentType,
    AttributeContent contentItem,
    int version
)
{}
