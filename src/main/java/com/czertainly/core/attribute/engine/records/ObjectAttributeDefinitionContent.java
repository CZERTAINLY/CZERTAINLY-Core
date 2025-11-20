package com.czertainly.core.attribute.engine.records;

import com.czertainly.api.model.common.attribute.v2.BaseAttributeV2;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContentV2;

import java.util.UUID;

public record ObjectAttributeDefinitionContent(
    UUID uuid,
    BaseAttributeV2 definition,
    BaseAttributeContentV2<?> contentItem
)
{}
