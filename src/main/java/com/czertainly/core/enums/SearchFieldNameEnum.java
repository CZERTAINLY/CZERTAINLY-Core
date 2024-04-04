package com.czertainly.core.enums;

import com.czertainly.api.model.core.search.SearchableFields;

public enum SearchFieldNameEnum {

    // Certificate
    COMMON_NAME(SearchableFields.COMMON_NAME, "Common Name", SearchFieldTypeEnum.STRING, false),
    SERIAL_NUMBER_LABEL(SearchableFields.SERIAL_NUMBER, "Serial Number", SearchFieldTypeEnum.STRING, false),
    RA_PROFILE(SearchableFields.RA_PROFILE_NAME, "RA Profile", SearchFieldTypeEnum.LIST, true),
    CERTIFICATE_STATE(SearchableFields.CERTIFICATE_STATE, "State", SearchFieldTypeEnum.LIST, false),
    CERTIFICATE_VALIDATION_STATUS(SearchableFields.CERTIFICATE_VALIDATION_STATUS, "Validation status", SearchFieldTypeEnum.LIST, false),
    GROUP(SearchableFields.GROUP_NAME, "Group", SearchFieldTypeEnum.LIST, true),
    OWNER(SearchableFields.OWNER, "Owner", SearchFieldTypeEnum.STRING, true),
    ISSUER_COMMON_NAME(SearchableFields.ISSUER_COMMON_NAME, "Issuer Common Name", SearchFieldTypeEnum.STRING, false),
    SIGNATURE_ALGORITHM(SearchableFields.SIGNATURE_ALGORITHM, "Signature Algorithm", SearchFieldTypeEnum.LIST, false),
    FINGERPRINT(SearchableFields.FINGERPRINT, "Fingerprint", SearchFieldTypeEnum.STRING, false),
    EXPIRES(SearchableFields.NOT_AFTER, "Expires At", SearchFieldTypeEnum.DATE, false),
    NOT_BEFORE(SearchableFields.NOT_BEFORE, "Valid From", SearchFieldTypeEnum.DATE, false),
    PUBLIC_KEY_ALGORITHM(SearchableFields.PUBLIC_KEY_ALGORITHM, "Public Key Algorithm", SearchFieldTypeEnum.LIST, false),
    KEY_SIZE(SearchableFields.KEY_SIZE, "Key Size", SearchFieldTypeEnum.LIST, false),
    KEY_USAGE(SearchableFields.KEY_USAGE, "Key Usage", SearchFieldTypeEnum.LIST, false),
    BASIC_CONSTRAINTS(SearchableFields.BASIC_CONSTRAINTS, "Basic Constraints", SearchFieldTypeEnum.LIST, false),
    SUBJECT_ALTERNATIVE(SearchableFields.SUBJECT_ALTERNATIVE_NAMES, "Subject Alternative Name", SearchFieldTypeEnum.STRING, false),
    SUBJECT_DN(SearchableFields.SUBJECTDN, "Subject DN", SearchFieldTypeEnum.STRING, false),
    ISSUER_DN(SearchableFields.ISSUERDN, "Issuer DN", SearchFieldTypeEnum.STRING, false),
    ISSUER_SERIAL_NUMBER(SearchableFields.ISSUER_SERIAL_NUMBER, "Issuer Serial Number", SearchFieldTypeEnum.STRING, false),
    OCSP_VALIDATION(SearchableFields.OCSP_VALIDATION, "OCSP Validation", SearchFieldTypeEnum.LIST, false),
    CRL_VALIDATION(SearchableFields.CRL_VALIDATION, "CRL Validation", SearchFieldTypeEnum.LIST, false),
    SIGNATURE_VALIDATION(SearchableFields.SIGNATURE_VALIDATION, "Signature Validation", SearchFieldTypeEnum.LIST, false),
    COMPLIANCE_STATUS(SearchableFields.COMPLIANCE_STATUS, "Compliance Status", SearchFieldTypeEnum.LIST, false),
    PRIVATE_KEY(SearchableFields.PRIVATE_KEY, "Has private key", SearchFieldTypeEnum.BOOLEAN, false),
    TRUSTED_CA(SearchableFields.TRUSTED_CA, "Trusted CA", SearchFieldTypeEnum.BOOLEAN, true),

    // Cryptographic Key
    NAME(SearchableFields.NAME, "Name", SearchFieldTypeEnum.STRING, false),
    KEY_TYPE(SearchableFields.CKI_TYPE, "Key type", SearchFieldTypeEnum.LIST, false),
    KEY_FORMAT(SearchableFields.CKI_FORMAT, "Key format", SearchFieldTypeEnum.LIST, false),
    KEY_STATE(SearchableFields.CKI_STATE, "State", SearchFieldTypeEnum.LIST, true),
    KEY_CRYPTOGRAPHIC_ALGORITHM(SearchableFields.CKI_CRYPTOGRAPHIC_ALGORITHM, "Cryptographic algorithm", SearchFieldTypeEnum.LIST, false),
    KEY_TOKEN_PROFILE(SearchableFields.CK_TOKEN_PROFILE, "Token profile", SearchFieldTypeEnum.LIST, false),
    KEY_TOKEN_INSTANCE_LABEL(SearchableFields.CK_TOKEN_INSTANCE, "Token instance", SearchFieldTypeEnum.LIST, true),
    KEY_LENGTH(SearchableFields.CKI_LENGTH, "Key Size", SearchFieldTypeEnum.NUMBER, false),
    CK_GROUP(SearchableFields.CK_GROUP, "Group", SearchFieldTypeEnum.LIST, true),
    CK_OWNER(SearchableFields.CK_OWNER, "Owner", SearchFieldTypeEnum.STRING, true),
    CK_KEY_USAGE(SearchableFields.CKI_USAGE, "Key Usage", SearchFieldTypeEnum.LIST, true),

    // Discovery
    START_TIME(SearchableFields.START_TIME, "Start time", SearchFieldTypeEnum.DATETIME, false),
    END_TIME(SearchableFields.END_TIME, "End time", SearchFieldTypeEnum.DATETIME, false),
    TOTAL_CERT_DISCOVERED(SearchableFields.TOTAL_CERT_DISCOVERED, "Total certificate discovered", SearchFieldTypeEnum.NUMBER, false),
    CONNECTOR_NAME(SearchableFields.CONNECTOR_NAME, "Discovery provider",SearchFieldTypeEnum.LIST, false),
    KIND(SearchableFields.KIND, "Kind",SearchFieldTypeEnum.STRING, false),
    DISCOVERY_STATUS(SearchableFields.DISCOVERY_STATUS, "Status", SearchFieldTypeEnum.LIST, false),

    // Entity
    ENTITY_NAME(SearchableFields.NAME, "Name", SearchFieldTypeEnum.STRING, false),
    ENTITY_CONNECTOR_NAME(SearchableFields.CONNECTOR_NAME, "Entity provider", SearchFieldTypeEnum.LIST, false),
    ENTITY_KIND(SearchableFields.KIND, "Kind", SearchFieldTypeEnum.LIST, false),

    // Location
    LOCATION_NAME(SearchableFields.NAME, "Name", SearchFieldTypeEnum.STRING, false),
    LOCATION_INSTANCE_NAME(SearchableFields.ENTITY_INSTANCE_NAME, "Entity instance", SearchFieldTypeEnum.LIST, false),
    LOCATION_ENABLED(SearchableFields.ENABLED, "Enabled", SearchFieldTypeEnum.BOOLEAN, true),
    LOCATION_SUPPORT_MULTIPLE_ENTRIES(SearchableFields.SUPPORT_MULTIPLE_ENTRIES, "Support multiple entries", SearchFieldTypeEnum.BOOLEAN, false),
    LOCATION_SUPPORT_KEY_MANAGEMENT(SearchableFields.SUPPORT_KEY_MANAGEMENT, "Support key management", SearchFieldTypeEnum.BOOLEAN, false),
    ;


    private SearchableFields fieldProperty;

    private String fieldLabel;

    private SearchFieldTypeEnum fieldTypeEnum;

    private boolean settable;

    SearchFieldNameEnum(final SearchableFields fieldProperty, final String fieldLabel, final SearchFieldTypeEnum fieldTypeEnum, final boolean settable) {
        this.fieldProperty = fieldProperty;
        this.fieldLabel = fieldLabel;
        this.fieldTypeEnum = fieldTypeEnum;
        this.settable = settable;
    }

    public SearchableFields getFieldProperty() {
        return fieldProperty;
    }

    public String getFieldLabel() {
        return fieldLabel;
    }

    public SearchFieldTypeEnum getFieldTypeEnum() {
        return fieldTypeEnum;
    }

    public boolean isSettable() {
        return settable;
    }

    public static SearchFieldNameEnum getEnumBySearchableFields(final SearchableFields searchableFields) {
        for (SearchFieldNameEnum searchFieldNameEnum : SearchFieldNameEnum.values()) {
            if (searchFieldNameEnum.getFieldProperty().equals(searchableFields)) {
                return searchFieldNameEnum;
            }
        }
        return null;
    }
}
