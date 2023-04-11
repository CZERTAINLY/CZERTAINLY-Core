package com.czertainly.core.attribute;

public class RsaEncryptionAttributes {

    public static final String ATTRIBUTE_DATA_RSA_PADDING = "data_rsaPadding"; // this would be OAEP or PKCS1-v1_5 according the RFC 8017

    // additional attributes for OAEP only

    public static final String ATTRIBUTE_DATA_RSA_OAEP_HASH = "data_rsaOaepHash"; // this would be SHA-1, SHA-256, SHA-384, SHA-512

    public static final String ATTRIBUTE_DATA_RSA_OAEP_USE_MGF = "data_rsaOaepMgf"; // true or false, because only MGF1 is supported by the RFC 8017

}
