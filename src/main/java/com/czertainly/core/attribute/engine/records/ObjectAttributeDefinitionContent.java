package com.czertainly.core.attribute.engine.records;

import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.BaseContentAttribute;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;

import java.util.List;
import java.util.UUID;

public record ObjectAttributeDefinitionContent(
    UUID uuid,
    BaseAttribute definition,
    BaseAttributeContent<?> contentItem
)
{}
