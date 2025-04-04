package com.czertainly.core.attribute;

import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BooleanAttributeContent;
import com.czertainly.api.model.common.attribute.v2.properties.DataAttributeProperties;

import java.util.List;

public class MLDSASignatureAttributes {

    public static final String ATTRIBUTE_BOOLEAN_PREHASH = "data_mldsaPrehash";
    public static final String ATTRIBUTE_BOOLEAN_PREHASH_UUID = "81f20bdd-ec84-4a7f-9c9d-13efce16665a";
    public static final String ATTRIBUTE_BOOLEAN_PREHASH_LABEL = "Use Pre-Hash";
    public static final String ATTRIBUTE_BOOLEAN_PREHASH_DESCRIPTION = "When checked, SHA-512 pre-hash will be used for signature, otherwise the pure version of algorithm will be used";

    public static List<BaseAttribute> getMLDSASignatureAttributes() {
        return List.of(
                buildBooleanPreHash()
        );
    }

    public static DataAttribute buildBooleanPreHash() {
        // define Data Attribute
        DataAttribute attribute = new DataAttribute();
        attribute.setUuid(ATTRIBUTE_BOOLEAN_PREHASH_UUID);
        attribute.setName(ATTRIBUTE_BOOLEAN_PREHASH);
        attribute.setDescription(ATTRIBUTE_BOOLEAN_PREHASH_DESCRIPTION);
        attribute.setType(AttributeType.DATA);
        attribute.setContentType(AttributeContentType.BOOLEAN);

        // create properties
        DataAttributeProperties attributeProperties = new DataAttributeProperties();
        attributeProperties.setLabel(ATTRIBUTE_BOOLEAN_PREHASH_LABEL);
        attributeProperties.setRequired(true);
        attributeProperties.setVisible(true);
        attributeProperties.setList(false);
        attributeProperties.setMultiSelect(false);
        attributeProperties.setReadOnly(false);
        attribute.setProperties(attributeProperties);

        // Set content
        attribute.setContent(List.of(new BooleanAttributeContent(false)));

        return attribute;
    }

    private MLDSASignatureAttributes() {
    }

}
