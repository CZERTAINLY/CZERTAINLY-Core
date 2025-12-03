package com.czertainly.core.attribute.engine.records;

import com.czertainly.api.model.client.attribute.*;
import com.czertainly.api.model.common.attribute.common.AttributeContent;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContentV2;
import com.czertainly.api.model.common.attribute.v3.content.BaseAttributeContentV3;

import java.util.List;
import java.util.UUID;

public class AttributeVersionFactory {

    private AttributeVersionFactory(){}

    public static ResponseAttribute getResponseAttribute(UUID uuid, String name, String label, List<? extends AttributeContent> content, AttributeContentType contentType, AttributeType attributeType, int version) {
        if (version == 2) {
            return getResponseAttributeV2(uuid, name, label, content, contentType, attributeType);
        }
        if (version == 3) {
            return getResponseAttributeV3(uuid, name, label, content, contentType, attributeType);
        }
        return null;
    }


    public static RequestAttribute getRequestAttribute(UUID uuid, String name, List<? extends AttributeContent> content, AttributeContentType contentType, int version) {
        if (version == 2) {
            return getRequestAttributeV2(uuid, name, content, contentType);
        }
        if (version == 3) {
            return getRequestAttributeV3(uuid, name, content, contentType);
        }
        return null;
    }

    public static void addRequestAttributeContent(RequestAttribute requestAttribute, AttributeContent contentItem, int version) {
        if (version == 2) {
            addRequestAttributeContentV2(requestAttribute, contentItem);
        }
        if (version == 3) {
            addRequestAttributeContentV3(requestAttribute, contentItem);
        }
    }

    public static void addResponseAttributeContent(ResponseAttribute requestAttribute, AttributeContent contentItem, int version) {
        if (version == 2) {
            addResponseAttributeContentV2(requestAttribute, contentItem);
        }
        if (version == 3) {
            addResponseAttributeContentV3(requestAttribute, contentItem);
        }
    }


    private static ResponseAttribute getResponseAttributeV2(UUID uuid, String name, String label, List<? extends AttributeContent> content, AttributeContentType contentType, AttributeType attributeType) {
        return new ResponseAttributeV2Dto((List<BaseAttributeContentV2<?>>) content, uuid, name, label, attributeType, contentType);
    }


    private static RequestAttribute getRequestAttributeV2(UUID uuid, String name, List<? extends AttributeContent> content, AttributeContentType contentType) {
        return new RequestAttributeV2Dto(uuid, name, contentType, (List<BaseAttributeContentV2<?>>) content);
    }


    private static void addRequestAttributeContentV2(RequestAttribute requestAttribute, AttributeContent contentItem) {
        ((RequestAttributeV2Dto) requestAttribute).getContent().add((BaseAttributeContentV2<?>) contentItem);
    }


    private static void addResponseAttributeContentV2(ResponseAttribute requestAttribute, AttributeContent contentItem) {
        ((ResponseAttributeV2Dto) requestAttribute).getContent().add((BaseAttributeContentV2<?>) contentItem);
    }


    private static ResponseAttribute getResponseAttributeV3(UUID uuid, String name, String label, List<? extends AttributeContent> content, AttributeContentType contentType, AttributeType attributeType) {
        return new ResponseAttributeV3Dto((List<BaseAttributeContentV3<?>>) content, uuid, name, label, attributeType, contentType);
    }


    private static RequestAttribute getRequestAttributeV3(UUID uuid, String name, List<? extends AttributeContent> content, AttributeContentType contentType) {
        return new RequestAttributeV3Dto(uuid, name, contentType, (List<BaseAttributeContentV3<?>>) content);
    }


    private static void addRequestAttributeContentV3(RequestAttribute requestAttribute, AttributeContent contentItem) {
        ((RequestAttributeV3Dto) requestAttribute).getContent().add((BaseAttributeContentV3<?>) contentItem);
    }


    private static void addResponseAttributeContentV3(ResponseAttribute requestAttribute, AttributeContent contentItem) {
        ((ResponseAttributeV3Dto) requestAttribute).getContent().add((BaseAttributeContentV3<?>) contentItem);
    }
}
