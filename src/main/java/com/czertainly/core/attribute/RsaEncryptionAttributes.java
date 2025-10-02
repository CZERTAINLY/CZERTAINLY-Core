package com.czertainly.core.attribute;

import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BooleanAttributeContent;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContent;
import com.czertainly.api.model.common.attribute.v2.properties.DataAttributeProperties;
import com.czertainly.api.model.common.enums.cryptography.DigestAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.RsaEncryptionScheme;
import com.czertainly.core.attribute.engine.AttributeOperation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class RsaEncryptionAttributes {

    private RsaEncryptionAttributes() {}

    public static final String ATTRIBUTE_DATA_RSA_ENC_SCHEME_NAME = "data_rsaEncScheme"; // this would be OAEP or PKCS1-v1_5 according the RFC 8017
    public static final String ATTRIBUTE_DATA_RSA_ENC_SCHEME_UUID = "6a93364c-d9d4-11ed-afa1-0242ac120002";
    public static final String ATTRIBUTE_DATA_RSA_ENC_SCHEME_LABEL = "RSA Encryption Scheme";
    public static final String ATTRIBUTE_DATA_RSA_ENC_SCHEME_DESCRIPTION = "RSA Encryption Scheme to use";

    // additional attributes for OAEP only

    public static final String ATTRIBUTE_DATA_RSA_OAEP_HASH_NAME = "data_rsaOaepHash"; // this would be SHA-1, SHA-256, SHA-384, SHA-512
    public static final String ATTRIBUTE_DATA_RSA_OAEP_HASH_UUID = "6a933a52-d9d4-11ed-afa1-0242ac120002";
    public static final String ATTRIBUTE_DATA_RSA_OAEP_HASH_LABEL = "OAEP Hash";
    public static final String ATTRIBUTE_DATA_RSA_OAEP_HASH_DESCRIPTION = "OAEP Hash Function";

    public static final String ATTRIBUTE_DATA_RSA_OAEP_USE_MGF_NAME = "data_rsaOaepMgf"; // true or false, because only MGF1 is supported by the RFC 8017
    public static final String ATTRIBUTE_DATA_RSA_OAEP_USE_MGF_UUID = "6a933c32-d9d4-11ed-afa1-0242ac120002";
    public static final String ATTRIBUTE_DATA_RSA_OAEP_USE_MGF_LABEL = "OAEP MGF";
    public static final String ATTRIBUTE_DATA_RSA_OAEP_USE_MGF_DESCRIPTION = "Usage of OAEP MGF";


    @CoreAttributeDefinitions(operation = AttributeOperation.ENCRYPT)
    public static List<BaseAttribute> getRsaEncryptionAttributes() {
        return List.of(
                buildDataEncryptionScheme(),
                buildDataOaepHash(),
                buildDataOaepMgf()
        );
    }

    public static BaseAttribute buildDataEncryptionScheme() {
        // define Data Attribute
        DataAttribute attribute = new DataAttribute();
        attribute.setUuid(ATTRIBUTE_DATA_RSA_ENC_SCHEME_UUID);
        attribute.setName(ATTRIBUTE_DATA_RSA_ENC_SCHEME_NAME);
        attribute.setDescription(ATTRIBUTE_DATA_RSA_ENC_SCHEME_DESCRIPTION);
        attribute.setType(AttributeType.DATA);
        attribute.setContentType(AttributeContentType.STRING);
        attribute.setContent(
                Stream.of(RsaEncryptionScheme.values())
                        .map(item -> new StringAttributeContent(item.getLabel(), item.getCode()))
                        .collect(Collectors.toList())
        );
        // create properties
        DataAttributeProperties attributeProperties = new DataAttributeProperties();
        attributeProperties.setLabel(ATTRIBUTE_DATA_RSA_ENC_SCHEME_LABEL);
        attributeProperties.setRequired(false);
        attributeProperties.setVisible(true);
        attributeProperties.setList(true);
        attributeProperties.setMultiSelect(false);
        attributeProperties.setReadOnly(false);
        attribute.setProperties(attributeProperties);
        return attribute;
    }

    public static BaseAttribute buildDataOaepMgf() {
        // define Data Attribute
        DataAttribute attribute = new DataAttribute();
        attribute.setUuid(ATTRIBUTE_DATA_RSA_OAEP_USE_MGF_UUID);
        attribute.setName(ATTRIBUTE_DATA_RSA_OAEP_USE_MGF_NAME);
        attribute.setDescription(ATTRIBUTE_DATA_RSA_OAEP_USE_MGF_DESCRIPTION);
        attribute.setType(AttributeType.DATA);
        attribute.setContentType(AttributeContentType.BOOLEAN);
        // create properties
        DataAttributeProperties attributeProperties = new DataAttributeProperties();
        attributeProperties.setLabel(ATTRIBUTE_DATA_RSA_OAEP_USE_MGF_LABEL);
        attributeProperties.setRequired(false);
        attributeProperties.setVisible(true);
        attributeProperties.setList(false);
        attributeProperties.setMultiSelect(false);
        attributeProperties.setReadOnly(false);
        attribute.setProperties(attributeProperties);
        return attribute;
    }

    public static BaseAttribute buildDataOaepHash() {
        // define Data Attribute
        DataAttribute attribute = new DataAttribute();
        attribute.setUuid(ATTRIBUTE_DATA_RSA_OAEP_HASH_UUID);
        attribute.setName(ATTRIBUTE_DATA_RSA_OAEP_HASH_NAME);
        attribute.setDescription(ATTRIBUTE_DATA_RSA_OAEP_HASH_DESCRIPTION);
        attribute.setType(AttributeType.DATA);
        attribute.setContentType(AttributeContentType.STRING);
        attribute.setContent(
                Stream.of(DigestAlgorithm.values())
                        .map(item -> new StringAttributeContent(item.getLabel(), item.getCode()))
                        .collect(Collectors.toList())
        );
        // create properties
        DataAttributeProperties attributeProperties = new DataAttributeProperties();
        attributeProperties.setLabel(ATTRIBUTE_DATA_RSA_OAEP_HASH_LABEL);
        attributeProperties.setRequired(true);
        attributeProperties.setVisible(true);
        attributeProperties.setList(true);
        attributeProperties.setMultiSelect(false);
        attributeProperties.setReadOnly(false);
        attribute.setProperties(attributeProperties);
        return attribute;
    }

    public static RequestAttributeDto buildRequestEncryptionScheme(RsaEncryptionScheme value) {
        // define Data Attribute
        RequestAttributeDto attribute = new RequestAttributeDto();
        attribute.setUuid(ATTRIBUTE_DATA_RSA_ENC_SCHEME_UUID);
        attribute.setName(ATTRIBUTE_DATA_RSA_ENC_SCHEME_NAME);
        attribute.setContentType(AttributeContentType.STRING);
        attribute.setContent(List.of(new StringAttributeContent(value.getCode())));
        return attribute;
    }

    public static RequestAttributeDto buildRequestOaepHash(DigestAlgorithm value) {
        // define Data Attribute
        RequestAttributeDto attribute = new RequestAttributeDto();
        attribute.setUuid(ATTRIBUTE_DATA_RSA_OAEP_HASH_UUID);
        attribute.setName(ATTRIBUTE_DATA_RSA_OAEP_HASH_NAME);
        attribute.setContentType(AttributeContentType.STRING);
        attribute.setContent(List.of(new StringAttributeContent(value.getCode())));
        return attribute;
    }

    public static RequestAttributeDto buildRequestOaepMgf(boolean value) {
        // define Data Attribute
        RequestAttributeDto attribute = new RequestAttributeDto();
        attribute.setUuid(ATTRIBUTE_DATA_RSA_OAEP_USE_MGF_UUID);
        attribute.setName(ATTRIBUTE_DATA_RSA_OAEP_USE_MGF_NAME);
        attribute.setContentType(AttributeContentType.BOOLEAN);
        attribute.setContent(List.of(new BooleanAttributeContent(value)));
        return attribute;
    }
}
