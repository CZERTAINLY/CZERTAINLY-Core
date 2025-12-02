package com.czertainly.core.attribute.engine.records;

import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.api.model.client.attribute.ResponseAttribute;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;

import java.util.List;
import java.util.UUID;

public interface AttributeVersionHandler<T> {

    ResponseAttribute getResponseAttribute(UUID uuid, String name, String label, List<T> content, AttributeContentType contentType, AttributeType attributeType);
    RequestAttribute getRequestAttribute(UUID uuid, String name, List<T> content, AttributeContentType contentType) ;
}
