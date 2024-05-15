package com.czertainly.core.enums;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.search.SearchableFields;

import java.util.Arrays;
import java.util.List;

public enum SearchFieldNameEnum {

    // Certificate
    COMMON_NAME(SearchableFields.COMMON_NAME, "Common Name", SearchFieldTypeEnum.STRING, false,  Resource.CERTIFICATE, null),
    SERIAL_NUMBER_LABEL(SearchableFields.SERIAL_NUMBER, "Serial Number", SearchFieldTypeEnum.STRING, false, Resource.CERTIFICATE, null),
    RA_PROFILE(SearchableFields.RA_PROFILE_NAME, "RA Profile", SearchFieldTypeEnum.LIST, true, Resource.CERTIFICATE, Resource.RA_PROFILE),
    CERTIFICATE_STATE(SearchableFields.CERTIFICATE_STATE, "State", SearchFieldTypeEnum.LIST, false, Resource.CERTIFICATE, null),
    CERTIFICATE_VALIDATION_STATUS(SearchableFields.CERTIFICATE_VALIDATION_STATUS, "Validation status", SearchFieldTypeEnum.LIST, false, Resource.CERTIFICATE, null),
    GROUP(SearchableFields.GROUP_NAME, "Groups", SearchFieldTypeEnum.LIST, true, Resource.CERTIFICATE, Resource.GROUP),
    OWNER(SearchableFields.OWNER, "Owner", SearchFieldTypeEnum.LIST, true, Resource.CERTIFICATE, Resource.USER),
    ISSUER_COMMON_NAME(SearchableFields.ISSUER_COMMON_NAME, "Issuer Common Name", SearchFieldTypeEnum.STRING, false, Resource.CERTIFICATE, null),
    SIGNATURE_ALGORITHM(SearchableFields.SIGNATURE_ALGORITHM, "Signature Algorithm", SearchFieldTypeEnum.LIST, false, Resource.CERTIFICATE, null),
    FINGERPRINT(SearchableFields.FINGERPRINT, "Fingerprint", SearchFieldTypeEnum.STRING, false, Resource.CERTIFICATE, null),
    EXPIRES(SearchableFields.NOT_AFTER, "Expires At", SearchFieldTypeEnum.DATE, false, Resource.CERTIFICATE, null),
    NOT_BEFORE(SearchableFields.NOT_BEFORE, "Valid From", SearchFieldTypeEnum.DATE, false, Resource.CERTIFICATE, null),
    PUBLIC_KEY_ALGORITHM(SearchableFields.PUBLIC_KEY_ALGORITHM, "Public Key Algorithm", SearchFieldTypeEnum.LIST, false, Resource.CERTIFICATE, null),
    KEY_SIZE(SearchableFields.KEY_SIZE, "Key Size", SearchFieldTypeEnum.LIST, false, Resource.CERTIFICATE, null),
    KEY_USAGE(SearchableFields.KEY_USAGE, "Key Usage", SearchFieldTypeEnum.LIST, false, Resource.CERTIFICATE, null),
    BASIC_CONSTRAINTS(SearchableFields.BASIC_CONSTRAINTS, "Basic Constraints", SearchFieldTypeEnum.LIST, false, Resource.CERTIFICATE, null),
    SUBJECT_ALTERNATIVE(SearchableFields.SUBJECT_ALTERNATIVE_NAMES, "Subject Alternative Name", SearchFieldTypeEnum.STRING, false, Resource.CERTIFICATE, null),
    SUBJECT_DN(SearchableFields.SUBJECTDN, "Subject DN", SearchFieldTypeEnum.STRING, false, Resource.CERTIFICATE, null),
    ISSUER_DN(SearchableFields.ISSUERDN, "Issuer DN", SearchFieldTypeEnum.STRING, false, Resource.CERTIFICATE, null),
    ISSUER_SERIAL_NUMBER(SearchableFields.ISSUER_SERIAL_NUMBER, "Issuer Serial Number", SearchFieldTypeEnum.STRING, false, Resource.CERTIFICATE, null),
    OCSP_VALIDATION(SearchableFields.OCSP_VALIDATION, "OCSP Validation", SearchFieldTypeEnum.LIST, false, Resource.CERTIFICATE, null),
    CRL_VALIDATION(SearchableFields.CRL_VALIDATION, "CRL Validation", SearchFieldTypeEnum.LIST, false, Resource.CERTIFICATE, null),
    SIGNATURE_VALIDATION(SearchableFields.SIGNATURE_VALIDATION, "Signature Validation", SearchFieldTypeEnum.LIST, false, Resource.CERTIFICATE, null),
    COMPLIANCE_STATUS(SearchableFields.COMPLIANCE_STATUS, "Compliance Status", SearchFieldTypeEnum.LIST, false, Resource.CERTIFICATE, null),
    PRIVATE_KEY(SearchableFields.PRIVATE_KEY, "Has private key", SearchFieldTypeEnum.BOOLEAN, false, Resource.CERTIFICATE, null),
    TRUSTED_CA(SearchableFields.TRUSTED_CA, "Trusted CA", SearchFieldTypeEnum.BOOLEAN, false, Resource.CERTIFICATE, null),

    // Cryptographic Key
    NAME(SearchableFields.NAME, "Name", SearchFieldTypeEnum.STRING, false, Resource.CRYPTOGRAPHIC_KEY, null),
    KEY_TYPE(SearchableFields.CKI_TYPE, "Key type", SearchFieldTypeEnum.LIST, false, Resource.CRYPTOGRAPHIC_KEY, null),
    KEY_FORMAT(SearchableFields.CKI_FORMAT, "Key format", SearchFieldTypeEnum.LIST, false, Resource.CRYPTOGRAPHIC_KEY, null),
    KEY_STATE(SearchableFields.CKI_STATE, "State", SearchFieldTypeEnum.LIST, true, Resource.CRYPTOGRAPHIC_KEY, null),
    KEY_CRYPTOGRAPHIC_ALGORITHM(SearchableFields.CKI_CRYPTOGRAPHIC_ALGORITHM, "Cryptographic algorithm", SearchFieldTypeEnum.LIST, false, Resource.CRYPTOGRAPHIC_KEY, null),
    KEY_TOKEN_PROFILE(SearchableFields.CK_TOKEN_PROFILE, "Token profile", SearchFieldTypeEnum.LIST, false, Resource.CRYPTOGRAPHIC_KEY, Resource.TOKEN_PROFILE),
    KEY_TOKEN_INSTANCE_LABEL(SearchableFields.CK_TOKEN_INSTANCE, "Token instance", SearchFieldTypeEnum.LIST, true, Resource.CRYPTOGRAPHIC_KEY, Resource.TOKEN),
    KEY_LENGTH(SearchableFields.CKI_LENGTH, "Key Size", SearchFieldTypeEnum.NUMBER, false, Resource.CRYPTOGRAPHIC_KEY, null),
    CK_GROUP(SearchableFields.CK_GROUP, "Groups", SearchFieldTypeEnum.LIST, true, Resource.CRYPTOGRAPHIC_KEY, Resource.GROUP),
    CK_OWNER(SearchableFields.CK_OWNER, "Owner", SearchFieldTypeEnum.LIST, true, Resource.CRYPTOGRAPHIC_KEY, Resource.USER),
    CK_KEY_USAGE(SearchableFields.CKI_USAGE, "Key Usage", SearchFieldTypeEnum.LIST, true, Resource.CRYPTOGRAPHIC_KEY, null),

    // Discovery
    START_TIME(SearchableFields.START_TIME, "Start time", SearchFieldTypeEnum.DATETIME, false, Resource.DISCOVERY, null),
    END_TIME(SearchableFields.END_TIME, "End time", SearchFieldTypeEnum.DATETIME, false, Resource.DISCOVERY, null),
    TOTAL_CERT_DISCOVERED(SearchableFields.TOTAL_CERT_DISCOVERED, "Total certificate discovered", SearchFieldTypeEnum.NUMBER, false, Resource.DISCOVERY, null),
    CONNECTOR_NAME(SearchableFields.CONNECTOR_NAME, "Discovery provider",SearchFieldTypeEnum.LIST, false, Resource.DISCOVERY, null),
    KIND(SearchableFields.KIND, "Kind",SearchFieldTypeEnum.STRING, false, Resource.DISCOVERY, null),
    DISCOVERY_STATUS(SearchableFields.DISCOVERY_STATUS, "Status", SearchFieldTypeEnum.LIST, false, Resource.DISCOVERY, null),

    // Entity
    ENTITY_NAME(SearchableFields.NAME, "Name", SearchFieldTypeEnum.STRING, false, Resource.ENTITY, null),
    ENTITY_CONNECTOR_NAME(SearchableFields.CONNECTOR_NAME, "Entity provider", SearchFieldTypeEnum.LIST, false, Resource.ENTITY, null),
    ENTITY_KIND(SearchableFields.KIND, "Kind", SearchFieldTypeEnum.LIST, false, Resource.ENTITY, null),

    // Location
    LOCATION_NAME(SearchableFields.NAME, "Name", SearchFieldTypeEnum.STRING, false, Resource.LOCATION, null),
    LOCATION_INSTANCE_NAME(SearchableFields.ENTITY_INSTANCE_NAME, "Entity instance", SearchFieldTypeEnum.LIST, false, Resource.LOCATION, Resource.ENTITY),
    LOCATION_ENABLED(SearchableFields.ENABLED, "Enabled", SearchFieldTypeEnum.BOOLEAN, true, Resource.LOCATION, null),
    LOCATION_SUPPORT_MULTIPLE_ENTRIES(SearchableFields.SUPPORT_MULTIPLE_ENTRIES, "Support multiple entries", SearchFieldTypeEnum.BOOLEAN, false, Resource.LOCATION, null),
    LOCATION_SUPPORT_KEY_MANAGEMENT(SearchableFields.SUPPORT_KEY_MANAGEMENT, "Support key management", SearchFieldTypeEnum.BOOLEAN, false, Resource.LOCATION, null),
    ;


    private final SearchableFields fieldProperty;

    private final String fieldLabel;

    private final SearchFieldTypeEnum fieldTypeEnum;

    private final boolean settable;

    private final Resource resource;

    private final Resource fieldResource;

    SearchFieldNameEnum(final SearchableFields fieldProperty, final String fieldLabel, final SearchFieldTypeEnum fieldTypeEnum, final boolean settable, final Resource resource, final Resource fieldResource) {
        this.fieldProperty = fieldProperty;
        this.fieldLabel = fieldLabel;
        this.fieldTypeEnum = fieldTypeEnum;
        this.settable = settable;
        this.resource = resource;
        this.fieldResource = fieldResource;
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

    public Resource getResource() { return this.resource; }

    public Resource getFieldResource() {return this.fieldResource;}

    public static SearchFieldNameEnum getEnumBySearchableFields(final SearchableFields searchableFields) {
        for (SearchFieldNameEnum searchFieldNameEnum : SearchFieldNameEnum.values()) {
            if (searchFieldNameEnum.getFieldProperty().equals(searchableFields)) {
                return searchFieldNameEnum;
            }
        }
        return null;
    }

    public static List<SearchFieldNameEnum> getEnumsForResource(Resource resource) {
        return Arrays.stream(SearchFieldNameEnum.values()).filter(searchFieldNameEnum -> searchFieldNameEnum.resource == resource).toList();
    }
}
