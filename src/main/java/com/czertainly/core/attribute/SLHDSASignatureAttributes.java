package com.czertainly.core.attribute;

import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BooleanAttributeContent;
import com.czertainly.api.model.common.attribute.v2.properties.DataAttributeProperties;

import java.util.List;

public class SLHDSASignatureAttributes {

    private SLHDSASignatureAttributes() {
    }

    public static final String ATTRIBUTE_BOOLEAN_PREHASH = "boolean_slhdsaPrehash";
    public static final String ATTRIBUTE_BOOLEAN_PREHASH_UUID = "81f20bdd-ec84-4a7f-9c9d-13efce16665a";
    public static final String ATTRIBUTE_BOOLEAN_PREHASH_LABEL = "Use Pre-Hash";
    public static final String ATTRIBUTE_BOOLEAN_PREHASH_DESCRIPTION = "When checked, pre-hash will be used for signature. Hash algorithm depends on other SLH-DSA parameters - SHA2 will be used when SHA2 is used in algorithm (SHA2-256 for security category 1, SHA-512 for categories 3 and 5) " +
            "and SHAKE for SHAKE used in algorithm (SHAKE128 for security category 1, SHAKE256 for categories 3 and 5)." +
            "Otherwise the pure version of algorithm will be used.";

    public static List<BaseAttribute> getSLHDSASignatureAttributes() {
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

}
