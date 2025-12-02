package com.czertainly.core.attribute.engine.records;

import com.czertainly.api.model.client.attribute.*;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContentV2;
import com.czertainly.api.model.common.attribute.v3.content.BaseAttributeContentV3;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component("3")
public class AttributeVersion3Handler implements AttributeVersionHandler<BaseAttributeContentV3<?>> {

    @Override
    public ResponseAttribute getResponseAttribute(UUID uuid, String name, String label, List<BaseAttributeContentV3<?>> content, AttributeContentType contentType, AttributeType attributeType) {
        return new ResponseAttributeV3Dto(content, uuid, name, label, attributeType, contentType);
    }

    @Override
    public RequestAttribute getRequestAttribute(UUID uuid, String name, List<BaseAttributeContentV3<?>> content, AttributeContentType contentType) {
        return new RequestAttributeV3Dto(uuid, name, contentType, content);
    }
}
