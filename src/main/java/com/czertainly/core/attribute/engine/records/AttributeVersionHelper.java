package com.czertainly.core.attribute.engine.records;

import com.czertainly.api.model.client.attribute.*;
import com.czertainly.api.model.client.metadata.ResponseMetadata;
import com.czertainly.api.model.client.metadata.ResponseMetadataV2;
import com.czertainly.api.model.client.metadata.ResponseMetadataV3;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.common.AttributeContent;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.common.attribute.common.AttributeType;
import com.czertainly.api.model.common.attribute.v2.GroupAttributeV2;
import com.czertainly.api.model.common.attribute.common.callback.AttributeCallback;
import com.czertainly.api.model.common.attribute.common.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContentV2;
import com.czertainly.api.model.common.attribute.v3.GroupAttributeV3;
import com.czertainly.api.model.common.attribute.v3.content.BaseAttributeContentV3;
import com.czertainly.core.dao.entity.AttributeDefinition;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AttributeVersionHelper {

    private AttributeVersionHelper(){}

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

    public static void addResponseAttributeContent(ResponseAttribute responseAttribute, AttributeContent contentItem, int version) {
        if (version == 2) {
            addResponseAttributeContentV2(responseAttribute, contentItem);
        }
        if (version == 3) {
            addResponseAttributeContentV3(responseAttribute, contentItem);
        }
    }

    public static ResponseMetadata getResponseMetadata(int version, List<NameAndUuidDto> sourceObjects, UUID uuid, String name, String label, AttributeType type, AttributeContentType contentType, List<? extends AttributeContent> content) {
        if (version == 2) {
            return getResponseMetadataV2(sourceObjects, uuid, name, label, type, contentType, content);
        }

        if (version == 3) {
            return getResponseMetadataV3(sourceObjects, uuid, name, label, type, contentType, content);
        }
        return null;
    }

    public static void addResponseMetadataContent(int version, ResponseMetadata responseMetadata, AttributeContent contentItem) {
        if (version == 2) {
            addResponseMetadataContentV2(responseMetadata, contentItem);
        }
        if (version == 3) {
            addResponseMetadataContentV3(responseMetadata, contentItem);
        }
    }


    private static void addResponseMetadataContentV2(ResponseMetadata responseMetadata, AttributeContent contentItem) {
        ResponseMetadataV2 responseMetadataV2 = (ResponseMetadataV2) responseMetadata;
        BaseAttributeContentV2<?> attributeContentV2 = (BaseAttributeContentV2<?>) contentItem;
        if (!responseMetadataV2.getContent().contains(contentItem)) {
            responseMetadataV2.getContent().add(attributeContentV2);
        }
    }

    private static void addResponseMetadataContentV3(ResponseMetadata responseMetadata, AttributeContent contentItem) {
        ResponseMetadataV3 responseMetadataV3 = (ResponseMetadataV3) responseMetadata;
        BaseAttributeContentV3<?> attributeContentV3 = (BaseAttributeContentV3<?>) contentItem;
        if (!responseMetadataV3.getContent().contains(contentItem)) {
            responseMetadataV3.getContent().add(attributeContentV3);
        }
    }


    private static ResponseMetadataV2 getResponseMetadataV2(List<NameAndUuidDto> sourceObjects, UUID uuid, String name, String label, AttributeType type, AttributeContentType contentType, List<? extends AttributeContent> content) {
        return new ResponseMetadataV2(sourceObjects, uuid, name, label, type, contentType, (List<BaseAttributeContentV2<?>>) content);
    }

    private static ResponseMetadataV3 getResponseMetadataV3(List<NameAndUuidDto> sourceObjects, UUID uuid, String name, String label, AttributeType type, AttributeContentType contentType, List<? extends AttributeContent> content) {
        return new ResponseMetadataV3(sourceObjects, uuid, name, label, type, contentType, (List<BaseAttributeContentV3<?>>) content);
    }

    private static ResponseAttributeV2 getResponseAttributeV2(UUID uuid, String name, String label, List<? extends AttributeContent> content, AttributeContentType contentType, AttributeType attributeType) {
        return new ResponseAttributeV2((List<BaseAttributeContentV2<?>>) content, uuid, name, label, attributeType, contentType);
    }


    private static RequestAttributeV2 getRequestAttributeV2(UUID uuid, String name, List<? extends AttributeContent> content, AttributeContentType contentType) {
        return new RequestAttributeV2(uuid, name, contentType, (List<BaseAttributeContentV2<?>>) content);
    }


    private static void addRequestAttributeContentV2(RequestAttribute requestAttribute, AttributeContent contentItem) {
        ((RequestAttributeV2) requestAttribute).getContent().add((BaseAttributeContentV2<?>) contentItem);
    }


    private static void addResponseAttributeContentV2(ResponseAttribute responseAttribute, AttributeContent contentItem) {
        ((ResponseAttributeV2) responseAttribute).getContent().add((BaseAttributeContentV2<?>) contentItem);
    }


    private static ResponseAttribute getResponseAttributeV3(UUID uuid, String name, String label, List<? extends AttributeContent> content, AttributeContentType contentType, AttributeType attributeType) {
        return new ResponseAttributeV3((List<BaseAttributeContentV3<?>>) content, uuid, name, label, attributeType, contentType);
    }


    private static RequestAttribute getRequestAttributeV3(UUID uuid, String name, List<? extends AttributeContent> content, AttributeContentType contentType) {
        return new RequestAttributeV3(uuid, name, contentType, (List<BaseAttributeContentV3<?>>) content);
    }


    private static void addRequestAttributeContentV3(RequestAttribute requestAttribute, AttributeContent contentItem) {
        ((RequestAttributeV3) requestAttribute).getContent().add((BaseAttributeContentV3<?>) contentItem);
    }


    private static void addResponseAttributeContentV3(ResponseAttribute responseAttribute, AttributeContent contentItem) {
        ((ResponseAttributeV3) responseAttribute).getContent().add((BaseAttributeContentV3<?>) contentItem);
    }

    public static AttributeCallback getGroupAttributeCallback(BaseAttribute attribute) {
        if (attribute.getVersion() == 2) return ((GroupAttributeV2) attribute).getAttributeCallback();
        if (attribute.getVersion() == 3) return ((GroupAttributeV3) attribute).getAttributeCallback();
        return null;
    }

    public static BaseAttributeContentV3<?> convertAttributeContentToV3(AttributeContent attributeContent, AttributeContentType contentType) {
        if (attributeContent.getContentType() != null) return (BaseAttributeContentV3<?>) attributeContent;
       else  {
            return convertV2ToV3((BaseAttributeContentV2<?>) attributeContent, contentType);
        }
    }

    private static <T extends Serializable> BaseAttributeContentV3<T> convertV2ToV3(
            BaseAttributeContentV2<T> v2,
            AttributeContentType contentType
    ) {
        BaseAttributeContentV3<T> v3 = new BaseAttributeContentV3<>();
        v3.setContentType(contentType);
        v3.setReference(v2.getReference());
        v3.setData(v2.getData());
        return v3;
    }

    public static List<BaseAttributeContentV3<?>> getBaseAttributeContentV3s(List<? extends AttributeContent> attributeContentItems, AttributeDefinition attributeDefinition) {
        List<BaseAttributeContentV3<?>> contentV3s = new ArrayList<>();
        for (AttributeContent content : attributeContentItems) {
            BaseAttributeContentV3<?> baseAttributeContentV3;
            if (content.getContentType() == null) {
                baseAttributeContentV3 = new BaseAttributeContentV3<>();
                baseAttributeContentV3.setContentType(attributeDefinition.getContentType());
                baseAttributeContentV3.setData(content.getData());
                baseAttributeContentV3.setReference(content.getReference());
            } else {
                baseAttributeContentV3 = (BaseAttributeContentV3<?>) content;
            }
            contentV3s.add((baseAttributeContentV3));
        }
        return contentV3s;
    }
}
