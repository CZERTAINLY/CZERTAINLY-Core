package com.czertainly.core.attribute;

import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BooleanAttributeContent;
import com.czertainly.api.model.common.attribute.v2.properties.DataAttributeProperties;
import com.czertainly.api.model.common.collection.DigestAlgorithm;

import java.util.List;

public class EncryptionAttributes {

    public static final String ATTRIBUTE_IS_CMS_NAME = "data_isCms";
    public static final String ATTRIBUTE_IS_CMS_UUID = "46bfdc2f-a96f-4f5d-a218-d538fde83d7a";
    public static final String ATTRIBUTE_IS_CMS_LABEL = "CMS Data";
    public static final String ATTRIBUTE_IS_CMS_DESCRIPTION = "Is the data to be decrypted in CMS format?";

    public static List<BaseAttribute> getEncryptionAttributes() {
        return List.of(
                buildCMS()
        );
    }

    public static BaseAttribute buildCMS() {
        // define Data Attribute
        DataAttribute attribute = new DataAttribute();
        attribute.setUuid(ATTRIBUTE_IS_CMS_UUID);
        attribute.setName(ATTRIBUTE_IS_CMS_NAME);
        attribute.setDescription(ATTRIBUTE_IS_CMS_DESCRIPTION);
        attribute.setType(AttributeType.DATA);
        attribute.setContentType(AttributeContentType.BOOLEAN);
        // create properties
        DataAttributeProperties attributeProperties = new DataAttributeProperties();
        attributeProperties.setLabel(ATTRIBUTE_IS_CMS_LABEL);
        attributeProperties.setRequired(true);
        attributeProperties.setVisible(true);
        attributeProperties.setList(true);
        attributeProperties.setMultiSelect(false);
        attributeProperties.setReadOnly(false);
        attribute.setProperties(attributeProperties);
        return attribute;
    }


    public static RequestAttributeDto buildCmsRequestAttribute(boolean value) {
        // define Data Attribute
        RequestAttributeDto attribute = new RequestAttributeDto();
        attribute.setUuid(ATTRIBUTE_IS_CMS_UUID);
        attribute.setName(ATTRIBUTE_IS_CMS_NAME);
        // set content
        attribute.setContent(List.of(new BooleanAttributeContent(value)));
        return attribute;
    }

}
