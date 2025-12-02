package com.czertainly.core.attribute.engine.records;

import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.api.model.client.attribute.RequestAttributeV2Dto;
import com.czertainly.api.model.client.attribute.ResponseAttribute;
import com.czertainly.api.model.client.attribute.ResponseAttributeV2Dto;
import com.czertainly.api.model.common.attribute.common.AttributeContent;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContentV2;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component("2")
public class AttributeVersion2Handler implements AttributeVersionHandler<BaseAttributeContentV2<?>> {
    @Override
    public ResponseAttribute getResponseAttribute(UUID uuid, String name, String label, List<BaseAttributeContentV2<?>> content, AttributeContentType contentType, AttributeType attributeType) {
        return new ResponseAttributeV2Dto(content, uuid, name, label, attributeType, contentType);
    }

    @Override
    public RequestAttribute getRequestAttribute(UUID uuid, String name, List<BaseAttributeContentV2<?>> content, AttributeContentType contentType) {
        return new RequestAttributeV2Dto(uuid, name, contentType, content);
    }

    @Override
    public void addRequestAttributeContent(RequestAttribute requestAttribute, AttributeContent contentItem) {
        ((RequestAttributeV2Dto) requestAttribute).getContent().add((BaseAttributeContentV2<?>) contentItem);
    }

    @Override
    public void addResponseAttributeContent(ResponseAttribute requestAttribute, AttributeContent contentItem) {
        ((ResponseAttributeV2Dto) requestAttribute).getContent().add((BaseAttributeContentV2<?>) contentItem);
    }
}
