package com.czertainly.core.attribute.engine.records;

import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.common.attribute.common.BaseAttributeContent;

import java.util.UUID;

public record ObjectAttributeDefinitionContent(
    UUID uuid,
    BaseAttribute definition,
    BaseAttributeContent contentItem
)
{}
