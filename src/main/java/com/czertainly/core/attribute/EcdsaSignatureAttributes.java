package com.czertainly.core.attribute;

import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContent;
import com.czertainly.api.model.common.attribute.v2.properties.DataAttributeProperties;
import com.czertainly.api.model.common.enums.cryptography.DigestAlgorithm;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EcdsaSignatureAttributes {

    public static final String ATTRIBUTE_DATA_SIG_DIGEST = "data_sigDigest";
    public static final String ATTRIBUTE_DATA_SIG_DIGEST_UUID = "46bfdc2f-a96f-4f5d-a218-d538fde92e6d";
    public static final String ATTRIBUTE_DATA_SIG_DIGEST_LABEL = "Digest Algorithm";
    public static final String ATTRIBUTE_DATA_SIG_DIGEST_DESCRIPTION = "Select on of the available digest (hash) algorithm";

    public static List<BaseAttribute> getEcdsaSignatureAttributes() {
        return List.of(
                buildDataDigest()
        );
    }

    public static BaseAttribute buildDataDigest() {
        // define Data Attribute
        DataAttribute attribute = new DataAttribute();
        attribute.setUuid(ATTRIBUTE_DATA_SIG_DIGEST_UUID);
        attribute.setName(ATTRIBUTE_DATA_SIG_DIGEST);
        attribute.setDescription(ATTRIBUTE_DATA_SIG_DIGEST_DESCRIPTION);
        attribute.setType(AttributeType.DATA);
        attribute.setContentType(AttributeContentType.STRING);
        // create properties
        DataAttributeProperties attributeProperties = new DataAttributeProperties();
        attributeProperties.setLabel(ATTRIBUTE_DATA_SIG_DIGEST_LABEL);
        attributeProperties.setRequired(true);
        attributeProperties.setVisible(true);
        attributeProperties.setList(true);
        attributeProperties.setMultiSelect(false);
        attributeProperties.setReadOnly(false);
        attribute.setProperties(attributeProperties);
        // set content
        attribute.setContent(
                Stream.of(DigestAlgorithm.values())
                        .map(item -> new StringAttributeContent(item.getLabel(), item.getCode()))
                        .collect(Collectors.toList())
        );

        return attribute;
    }

    public static RequestAttributeDto buildRequestDigest(DigestAlgorithm value) {
        RequestAttributeDto attribute = new RequestAttributeDto();
        attribute.setUuid(ATTRIBUTE_DATA_SIG_DIGEST_UUID);
        attribute.setName(ATTRIBUTE_DATA_SIG_DIGEST);
        attribute.setContent(List.of(new StringAttributeContent(value.getCode())));
        return attribute;
    }

}
