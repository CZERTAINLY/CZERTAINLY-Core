package com.czertainly.core.enums;

import com.czertainly.api.model.core.search.SearchableFields;

public enum SearchFieldNameEnum {

    COMMON_NAME(SearchableFields.COMMON_NAME, "Common Name", SearchFieldTypeEnum.STRING),
    SERIAL_NUMBER_LABEL(SearchableFields.SERIAL_NUMBER, "Serial Number", SearchFieldTypeEnum.STRING),
    RA_PROFILE(SearchableFields.RA_PROFILE_NAME, "RA Profile", SearchFieldTypeEnum.LIST),
    ENTITY(SearchableFields.ENTITY_NAME, "Entity", SearchFieldTypeEnum.LIST),
    STATUS(SearchableFields.STATUS, "Status", SearchFieldTypeEnum.LIST),
    GROUP(SearchableFields.GROUP_NAME, "Group", SearchFieldTypeEnum.LIST),
    OWNER(SearchableFields.OWNER, "Owner", SearchFieldTypeEnum.STRING),
    ISSUER_COMMON_NAME(SearchableFields.ISSUER_COMMON_NAME, "Issuer Common Name", SearchFieldTypeEnum.STRING),
    SIGNATURE_ALGORITHM(SearchableFields.SIGNATURE_ALGORITHM, "Signature Algorithm", SearchFieldTypeEnum.LIST),
    FINGERPRINT(SearchableFields.FINGERPRINT, "Fingerprint", SearchFieldTypeEnum.STRING),
    EXPIRES(SearchableFields.NOT_AFTER, "Expires At", SearchFieldTypeEnum.DATE),
    NOT_BEFORE(SearchableFields.NOT_BEFORE, "Valid From", SearchFieldTypeEnum.DATE),
    PUBLIC_KEY_ALGORITHM(SearchableFields.PUBLIC_KEY_ALGORITHM, "Public Key Algorithm", SearchFieldTypeEnum.LIST),
    KEY_SIZE(SearchableFields.KEY_SIZE, "Key Size", SearchFieldTypeEnum.LIST),
    KEY_USAGE(SearchableFields.KEY_USAGE, "Key Usage", SearchFieldTypeEnum.LIST),
    BASIC_CONSTRAINTS(SearchableFields.BASIC_CONSTRAINTS, "Basic Constraints", SearchFieldTypeEnum.LIST),
    SUBJECT_ALTERNATIVE(SearchableFields.SUBJECT_ALTERNATIVE_NAMES, "Subject Alternative Name", SearchFieldTypeEnum.STRING),
    SUBJECT_DN(SearchableFields.SUBJECTDN, "Subject DN", SearchFieldTypeEnum.STRING),
    ISSUER_DN(SearchableFields.ISSUERDN, "Issuer DN", SearchFieldTypeEnum.STRING),
    ISSUER_SERIAL_NUMBER(SearchableFields.ISSUER_SERIAL_NUMBER, "Issuer Serial Number", SearchFieldTypeEnum.STRING),
    OCSP_VALIDATION(SearchableFields.OCSP_VALIDATION, "OCSP Validation", SearchFieldTypeEnum.LIST),
    CRL_VALIDATION(SearchableFields.CRL_VALIDATION, "CRL Validation", SearchFieldTypeEnum.LIST),
    SIGNATURE_VALIDATION(SearchableFields.SIGNATURE_VALIDATION, "Signature Validation", SearchFieldTypeEnum.LIST),
    COMPLIANCE_STATUS(SearchableFields.COMPLIANCE_STATUS, "Compliance Status", SearchFieldTypeEnum.LIST),
    NAME(SearchableFields.NAME, "Name", SearchFieldTypeEnum.STRING),
    KEY_TYPE(SearchableFields.CKI_TYPE, "Key type", SearchFieldTypeEnum.LIST),
    KEY_FORMAT(SearchableFields.CKI_FORMAT, "Key format", SearchFieldTypeEnum.LIST),
    KEY_STATE(SearchableFields.CKI_STATE, "State", SearchFieldTypeEnum.LIST),
    KEY_CRYPTOGRAPHIC_ALGORITHM(SearchableFields.CKI_CRYPTOGRAPHIC_ALGORITHM, "Cryptographic algorithm", SearchFieldTypeEnum.LIST),
    KEY_TOKEN_PROFILE(SearchableFields.CK_TOKEN_PROFILE, "Token profile", SearchFieldTypeEnum.LIST),
    KEY_TOKEN_INSTANCE_LABEL(SearchableFields.CK_TOKEN_INSTANCE, "Token instance", SearchFieldTypeEnum.LIST),
    KEY_LENGTH(SearchableFields.CKI_LENGTH, "Key Size", SearchFieldTypeEnum.NUMBER),
    CK_GROUP(SearchableFields.CK_GROUP, "Group", SearchFieldTypeEnum.LIST),
    CK_OWNER(SearchableFields.CK_OWNER, "Owner", SearchFieldTypeEnum.STRING),
    CK_KEY_USAGE(SearchableFields.CKI_USAGE, "Key Usage", SearchFieldTypeEnum.LIST),
    START_TIME(SearchableFields.START_TIME, "Start time", SearchFieldTypeEnum.DATETIME),
    END_TIME(SearchableFields.END_TIME, "End time", SearchFieldTypeEnum.DATETIME),
    TOTAL_CERT_DISCOVERED(SearchableFields.TOTAL_CERT_DISCOVERED, "Total certificate discovered", SearchFieldTypeEnum.NUMBER),
    CONNECTOR_NAME(SearchableFields.CONNECTOR_NAME, "Connector name",SearchFieldTypeEnum.STRING),
    KIND(SearchableFields.KIND, "Kind",SearchFieldTypeEnum.STRING),
    DISCOVERY_STATUS(SearchableFields.DISCOVERY_STATUS, "Status", SearchFieldTypeEnum.LIST),;

    private SearchableFields fieldProperty;

    private String fieldLabel;

    private SearchFieldTypeEnum fieldTypeEnum;

    SearchFieldNameEnum(final SearchableFields fieldProperty, final String fieldLabel, final SearchFieldTypeEnum fieldTypeEnum) {
        this.fieldProperty = fieldProperty;
        this.fieldLabel = fieldLabel;
        this.fieldTypeEnum = fieldTypeEnum;
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

    public static SearchFieldNameEnum getEnumBySearchableFields(final SearchableFields searchableFields) {
        for (SearchFieldNameEnum searchFieldNameEnum : SearchFieldNameEnum.values()) {
            if (searchFieldNameEnum.getFieldProperty().equals(searchableFields)) {
                return searchFieldNameEnum;
            }
        }
        return null;
    }
    }
