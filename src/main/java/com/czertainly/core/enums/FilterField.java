package com.czertainly.core.enums;

import com.czertainly.api.model.common.enums.IPlatformEnum;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.compliance.ComplianceStatus;
import com.czertainly.core.dao.entity.*;
import jakarta.persistence.metamodel.Attribute;

import java.util.Arrays;
import java.util.List;

public enum FilterField {

    // Certificate
    COMMON_NAME(Resource.CERTIFICATE, null, null, Certificate_.commonName, "Common Name", SearchFieldTypeEnum.STRING),
    SERIAL_NUMBER(Resource.CERTIFICATE, null, null, Certificate_.serialNumber, "Serial Number", SearchFieldTypeEnum.STRING),
    RA_PROFILE_NAME(Resource.CERTIFICATE, Resource.RA_PROFILE, List.of(Certificate_.raProfile), RaProfile_.name, "RA Profile", SearchFieldTypeEnum.LIST, null, null, true),
    CERTIFICATE_STATE(Resource.CERTIFICATE, null, null, Certificate_.state, "State", SearchFieldTypeEnum.LIST, CertificateState.class),
    CERTIFICATE_VALIDATION_STATUS(Resource.CERTIFICATE, null, null, Certificate_.validationStatus, "Validation status", SearchFieldTypeEnum.LIST, CertificateValidationStatus.class),
    COMPLIANCE_STATUS(Resource.CERTIFICATE, null, null, Certificate_.complianceStatus, "Compliance Status", SearchFieldTypeEnum.LIST, ComplianceStatus.class),
    GROUP_NAME(Resource.CERTIFICATE, Resource.GROUP, List.of(Certificate_.groups), Group_.name, "Groups", SearchFieldTypeEnum.LIST, null, null, true),
    LOCATION_NAME(Resource.CERTIFICATE, Resource.LOCATION, List.of(Certificate_.locations, CertificateLocation_.location), Location_.name, "Locations", SearchFieldTypeEnum.LIST),
    OWNER(Resource.CERTIFICATE, Resource.USER, List.of(Certificate_.owner), OwnerAssociation_.ownerUsername, "Owner", SearchFieldTypeEnum.LIST, null, null, true),
    ISSUER_COMMON_NAME(Resource.CERTIFICATE, null, null, Certificate_.issuerCommonName, "Issuer Common Name", SearchFieldTypeEnum.STRING),
    SIGNATURE_ALGORITHM(Resource.CERTIFICATE, null, null, Certificate_.signatureAlgorithm, "Signature Algorithm", SearchFieldTypeEnum.LIST),
    FINGERPRINT(Resource.CERTIFICATE, null, null, Certificate_.fingerprint, "Fingerprint", SearchFieldTypeEnum.STRING),
    NOT_AFTER(Resource.CERTIFICATE, null, null, Certificate_.notAfter, "Expires At", SearchFieldTypeEnum.DATE),
    NOT_BEFORE(Resource.CERTIFICATE, null, null, Certificate_.notBefore, "Valid From", SearchFieldTypeEnum.DATE),
    PUBLIC_KEY_ALGORITHM(Resource.CERTIFICATE, null, null, Certificate_.publicKeyAlgorithm, "Public Key Algorithm", SearchFieldTypeEnum.LIST),
    KEY_SIZE(Resource.CERTIFICATE, null, null, Certificate_.keySize, "Key Size", SearchFieldTypeEnum.LIST),
    KEY_USAGE(Resource.CERTIFICATE, null, null, Certificate_.keyUsage, "Key Usage", SearchFieldTypeEnum.LIST),
    BASIC_CONSTRAINTS(Resource.CERTIFICATE, null, null, Certificate_.basicConstraints, "Basic Constraints", SearchFieldTypeEnum.LIST),
    SUBJECT_ALTERNATIVE_NAMES(Resource.CERTIFICATE, null, null, Certificate_.subjectAlternativeNames, "Subject Alternative Name", SearchFieldTypeEnum.STRING),
    SUBJECTDN(Resource.CERTIFICATE, null, null, Certificate_.subjectDn, "Subject DN", SearchFieldTypeEnum.STRING),
    ISSUERDN(Resource.CERTIFICATE, null, null, Certificate_.issuerDn, "Issuer DN", SearchFieldTypeEnum.STRING),
    ISSUER_SERIAL_NUMBER(Resource.CERTIFICATE, null, null, Certificate_.issuerSerialNumber, "Issuer Serial Number", SearchFieldTypeEnum.STRING),
    OCSP_VALIDATION(Resource.CERTIFICATE, null, null, Certificate_.certificateValidationResult, "OCSP Validation", SearchFieldTypeEnum.LIST, CertificateValidationStatus.class),
    CRL_VALIDATION(Resource.CERTIFICATE, null, null, Certificate_.certificateValidationResult, "CRL Validation", SearchFieldTypeEnum.LIST, CertificateValidationStatus.class),
    SIGNATURE_VALIDATION(Resource.CERTIFICATE, null, null, Certificate_.certificateValidationResult, "Signature Validation", SearchFieldTypeEnum.LIST, CertificateValidationStatus.class),
    PRIVATE_KEY(Resource.CERTIFICATE, Resource.CRYPTOGRAPHIC_KEY, List.of(Certificate_.key, CryptographicKey_.items), CryptographicKeyItem_.type, "Has private key", SearchFieldTypeEnum.BOOLEAN, null, KeyType.PRIVATE_KEY, false),
    TRUSTED_CA(Resource.CERTIFICATE, null, null, Certificate_.trustedCa, "Trusted CA", SearchFieldTypeEnum.BOOLEAN);
//
//    // Cryptographic Key
//    NAME(SearchableFields.NAME, "Name", SearchFieldTypeEnum.STRING, false, Resource.CRYPTOGRAPHIC_KEY, null),
//    KEY_TYPE(SearchableFields.CKI_TYPE, "Key type", SearchFieldTypeEnum.LIST, false, Resource.CRYPTOGRAPHIC_KEY, null),
//    KEY_FORMAT(SearchableFields.CKI_FORMAT, "Key format", SearchFieldTypeEnum.LIST, false, Resource.CRYPTOGRAPHIC_KEY, null),
//    KEY_STATE(SearchableFields.CKI_STATE, "State", SearchFieldTypeEnum.LIST, true, Resource.CRYPTOGRAPHIC_KEY, null),
//    KEY_CRYPTOGRAPHIC_ALGORITHM(SearchableFields.CKI_CRYPTOGRAPHIC_ALGORITHM, "Cryptographic algorithm", SearchFieldTypeEnum.LIST, false, Resource.CRYPTOGRAPHIC_KEY, null),
//    KEY_TOKEN_PROFILE(SearchableFields.CK_TOKEN_PROFILE, "Token profile", SearchFieldTypeEnum.LIST, false, Resource.CRYPTOGRAPHIC_KEY, Resource.TOKEN_PROFILE),
//    KEY_TOKEN_INSTANCE_LABEL(SearchableFields.CK_TOKEN_INSTANCE, "Token instance", SearchFieldTypeEnum.LIST, true, Resource.CRYPTOGRAPHIC_KEY, Resource.TOKEN),
//    KEY_LENGTH(SearchableFields.CKI_LENGTH, "Key Size", SearchFieldTypeEnum.NUMBER, false, Resource.CRYPTOGRAPHIC_KEY, null),
//    CK_GROUP(SearchableFields.CK_GROUP, "Groups", SearchFieldTypeEnum.LIST, true, Resource.CRYPTOGRAPHIC_KEY, Resource.GROUP),
//    CK_OWNER(SearchableFields.CK_OWNER, "Owner", SearchFieldTypeEnum.LIST, true, Resource.CRYPTOGRAPHIC_KEY, Resource.USER),
//    CK_KEY_USAGE(SearchableFields.CKI_USAGE, "Key Usage", SearchFieldTypeEnum.LIST, true, Resource.CRYPTOGRAPHIC_KEY, null),
//
//    // Discovery
//    START_TIME(SearchableFields.START_TIME, "Start time", SearchFieldTypeEnum.DATETIME, false, Resource.DISCOVERY, null),
//    END_TIME(SearchableFields.END_TIME, "End time", SearchFieldTypeEnum.DATETIME, false, Resource.DISCOVERY, null),
//    TOTAL_CERT_DISCOVERED(SearchableFields.TOTAL_CERT_DISCOVERED, "Total certificate discovered", SearchFieldTypeEnum.NUMBER, false, Resource.DISCOVERY, null),
//    CONNECTOR_NAME(SearchableFields.CONNECTOR_NAME, "Discovery provider",SearchFieldTypeEnum.LIST, false, Resource.DISCOVERY, null),
//    KIND(SearchableFields.KIND, "Kind",SearchFieldTypeEnum.STRING, false, Resource.DISCOVERY, null),
//    DISCOVERY_STATUS(SearchableFields.DISCOVERY_STATUS, "Status", SearchFieldTypeEnum.LIST, false, Resource.DISCOVERY, null),
//
//    // Entity
//    ENTITY_NAME(SearchableFields.NAME, "Name", SearchFieldTypeEnum.STRING, false, Resource.ENTITY, null),
//    ENTITY_CONNECTOR_NAME(SearchableFields.CONNECTOR_NAME, "Entity provider", SearchFieldTypeEnum.LIST, false, Resource.ENTITY, null),
//    ENTITY_KIND(SearchableFields.KIND, "Kind", SearchFieldTypeEnum.LIST, false, Resource.ENTITY, null),
//
//    // Location
//    LOCATION_NAME(SearchableFields.NAME, "Name", SearchFieldTypeEnum.STRING, false, Resource.LOCATION, null),
//    LOCATION_INSTANCE_NAME(SearchableFields.ENTITY_INSTANCE_NAME, "Entity instance", SearchFieldTypeEnum.LIST, false, Resource.LOCATION, Resource.ENTITY),
//    LOCATION_ENABLED(SearchableFields.ENABLED, "Enabled", SearchFieldTypeEnum.BOOLEAN, true, Resource.LOCATION, null),
//    LOCATION_SUPPORT_MULTIPLE_ENTRIES(SearchableFields.SUPPORT_MULTIPLE_ENTRIES, "Support multiple entries", SearchFieldTypeEnum.BOOLEAN, false, Resource.LOCATION, null),
//    LOCATION_SUPPORT_KEY_MANAGEMENT(SearchableFields.SUPPORT_KEY_MANAGEMENT, "Support key management", SearchFieldTypeEnum.BOOLEAN, false, Resource.LOCATION, null),
//    ;

    private static final FilterField[] VALUES;

    static {
        VALUES = values();
    }

    private final Resource rootResource;
    private final Resource fieldResource;
    private final List<Attribute> joinAttributes;
    private final Attribute fieldAttribute;
    private final SearchFieldTypeEnum type;
    private final String label;
    private final Class<? extends IPlatformEnum> enumClass;
    private final boolean settable;
    private final Object expectedValue;

    FilterField(final Resource rootResource, final Resource fieldResource, final List<Attribute> joinAttributes, final Attribute fieldAttribute, final String label, final SearchFieldTypeEnum type) {
        this(rootResource, fieldResource, joinAttributes, fieldAttribute, label, type, null, null, false);
    }

    FilterField(final Resource rootResource, final Resource fieldResource, final List<Attribute> joinAttributes, final Attribute fieldAttribute, final String label, final SearchFieldTypeEnum type, final Class<? extends IPlatformEnum> enumClass) {
        this(rootResource, fieldResource, joinAttributes, fieldAttribute, label, type, enumClass, null, false);
    }

    FilterField(final Resource rootResource, final Resource fieldResource, final List<Attribute> joinAttributes, final Attribute fieldAttribute, final String label, final SearchFieldTypeEnum type, final Class<? extends IPlatformEnum> enumClass, final Object expectedValue, final boolean settable) {
        this.rootResource = rootResource;
        this.fieldResource = fieldResource;
        this.joinAttributes = joinAttributes == null ? List.of() : joinAttributes;
        this.fieldAttribute = fieldAttribute;
        this.label = label;
        this.type = type;
        this.enumClass = enumClass;
        this.settable = settable;
        this.expectedValue = expectedValue;
    }

//    FilterFields(final SearchableFields fieldProperty, final String fieldLabel, final SearchFieldTypeEnum fieldTypeEnum, final boolean settable, final Resource resource, final Resource fieldResource) {
//        this.fieldProperty = fieldProperty;
//        this.fieldLabel = fieldLabel;
//        this.fieldTypeEnum = fieldTypeEnum;
//        this.settable = settable;
//        this.resource = resource;
//        this.fieldResource = fieldResource;
//    }

    public Resource getRootResource() {
        return rootResource;
    }

    public Resource getFieldResource() {
        return fieldResource;
    }

    public List<Attribute> getJoinAttributes() {
        return joinAttributes;
    }

    public Attribute getFieldAttribute() {
        return fieldAttribute;
    }

    public SearchFieldTypeEnum getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public Class<? extends IPlatformEnum> getEnumClass() {
        return enumClass;
    }

    public Object getExpectedValue() {
        return expectedValue;
    }

    public boolean isSettable() {
        return settable;
    }

    public static List<FilterField> getEnumsForResource(Resource resource) {
        return Arrays.stream(VALUES).filter(filterField -> filterField.rootResource == resource).toList();
    }

}
