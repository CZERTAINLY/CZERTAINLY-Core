package com.czertainly.core.enums;

import com.czertainly.api.model.client.approval.ApprovalStatusEnum;
import com.czertainly.api.model.common.enums.IPlatformEnum;
import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyFormat;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateSubjectType;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.compliance.ComplianceStatus;
import com.czertainly.api.model.core.cryptography.key.KeyState;
import com.czertainly.api.model.core.cryptography.key.KeyUsage;
import com.czertainly.api.model.core.discovery.DiscoveryStatus;
import com.czertainly.api.model.core.enums.CertificateProtocol;
import com.czertainly.api.model.core.logging.enums.*;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.oid.OidCategory;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.entity.oid.CustomOidEntry_;
import com.czertainly.core.dao.entity.oid.RdnAttributeTypeCustomOidEntry_;
import com.czertainly.core.model.auth.ResourceAction;
import jakarta.persistence.metamodel.Attribute;

import java.util.Arrays;
import java.util.List;

public enum FilterField {

    // Certificate
    COMMON_NAME(Resource.CERTIFICATE, null, null, Certificate_.commonName, "Common Name", SearchFieldTypeEnum.STRING),
    SERIAL_NUMBER(Resource.CERTIFICATE, null, null, Certificate_.serialNumber, "Serial Number", SearchFieldTypeEnum.STRING),
    RA_PROFILE_NAME(Resource.CERTIFICATE, Resource.RA_PROFILE, List.of(Certificate_.raProfile), RaProfile_.name, "RA Profile", SearchFieldTypeEnum.LIST, null, null, true, null),
    CERTIFICATE_STATE(Resource.CERTIFICATE, null, null, Certificate_.state, "State", SearchFieldTypeEnum.LIST, CertificateState.class),
    CERTIFICATE_VALIDATION_STATUS(Resource.CERTIFICATE, null, null, Certificate_.validationStatus, "Validation status", SearchFieldTypeEnum.LIST, CertificateValidationStatus.class),
    COMPLIANCE_STATUS(Resource.CERTIFICATE, null, null, Certificate_.complianceStatus, "Compliance Status", SearchFieldTypeEnum.LIST, ComplianceStatus.class),
    GROUP_NAME(Resource.CERTIFICATE, Resource.GROUP, List.of(Certificate_.groups), Group_.name, "Groups", SearchFieldTypeEnum.LIST, null, null, true, null),
    CERT_LOCATION_NAME(Resource.CERTIFICATE, Resource.LOCATION, List.of(Certificate_.locations, CertificateLocation_.location), Location_.name, "Locations", SearchFieldTypeEnum.LIST),
    OWNER(Resource.CERTIFICATE, Resource.USER, List.of(Certificate_.owner), OwnerAssociation_.ownerUsername, "Owner", SearchFieldTypeEnum.LIST, null, null, true, null),
    ISSUER_COMMON_NAME(Resource.CERTIFICATE, null, null, Certificate_.issuerCommonName, "Issuer Common Name", SearchFieldTypeEnum.STRING),
    SIGNATURE_ALGORITHM(Resource.CERTIFICATE, null, null, Certificate_.signatureAlgorithm, "Signature Algorithm", SearchFieldTypeEnum.LIST),
    ALT_SIGNATURE_ALGORITHM(Resource.CERTIFICATE, null, null, Certificate_.altSignatureAlgorithm, "Alternative Signature Algorithm", SearchFieldTypeEnum.LIST),
    FINGERPRINT(Resource.CERTIFICATE, null, null, Certificate_.fingerprint, "Fingerprint", SearchFieldTypeEnum.STRING),
    NOT_AFTER(Resource.CERTIFICATE, null, null, Certificate_.notAfter, "Expires At", SearchFieldTypeEnum.DATETIME),
    NOT_BEFORE(Resource.CERTIFICATE, null, null, Certificate_.notBefore, "Valid From", SearchFieldTypeEnum.DATETIME),
    PUBLIC_KEY_ALGORITHM(Resource.CERTIFICATE, null, null, Certificate_.publicKeyAlgorithm, "Public Key Algorithm", SearchFieldTypeEnum.LIST),
    ALT_PUBLIC_KEY_ALGORITHM(Resource.CERTIFICATE, null, null, Certificate_.altPublicKeyAlgorithm, "Alternative Public Key Algorithm", SearchFieldTypeEnum.LIST),
    KEY_SIZE(Resource.CERTIFICATE, null, null, Certificate_.keySize, "Key Size", SearchFieldTypeEnum.LIST),
    ALT_KEY_SIZE(Resource.CERTIFICATE, null, null, Certificate_.altKeySize, "Alternative Key Size", SearchFieldTypeEnum.LIST),
    KEY_USAGE(Resource.CERTIFICATE, null, null, Certificate_.keyUsage, "Key Usage", SearchFieldTypeEnum.LIST),
    SUBJECT_TYPE(Resource.CERTIFICATE, null, null, Certificate_.subjectType, "Subject Type", SearchFieldTypeEnum.LIST, CertificateSubjectType.class),
    SUBJECT_ALTERNATIVE_NAMES(Resource.CERTIFICATE, null, null, Certificate_.subjectAlternativeNames, "Subject Alternative Name", SearchFieldTypeEnum.STRING),
    SUBJECTDN(Resource.CERTIFICATE, null, null, Certificate_.subjectDn, "Subject DN", SearchFieldTypeEnum.STRING),
    ISSUERDN(Resource.CERTIFICATE, null, null, Certificate_.issuerDn, "Issuer DN", SearchFieldTypeEnum.STRING),
    ISSUER_SERIAL_NUMBER(Resource.CERTIFICATE, null, null, Certificate_.issuerSerialNumber, "Issuer Serial Number", SearchFieldTypeEnum.STRING),
    OCSP_VALIDATION(Resource.CERTIFICATE, null, null, Certificate_.certificateValidationResult, "OCSP Validation", SearchFieldTypeEnum.LIST, CertificateValidationStatus.class),
    CRL_VALIDATION(Resource.CERTIFICATE, null, null, Certificate_.certificateValidationResult, "CRL Validation", SearchFieldTypeEnum.LIST, CertificateValidationStatus.class),
    SIGNATURE_VALIDATION(Resource.CERTIFICATE, null, null, Certificate_.certificateValidationResult, "Signature Validation", SearchFieldTypeEnum.LIST, CertificateValidationStatus.class),
    PRIVATE_KEY(Resource.CERTIFICATE, Resource.CRYPTOGRAPHIC_KEY, List.of(Certificate_.key, CryptographicKey_.items), CryptographicKeyItem_.type, "Has private key", SearchFieldTypeEnum.BOOLEAN, null, KeyType.PRIVATE_KEY, false, null),
    TRUSTED_CA(Resource.CERTIFICATE, null, null, Certificate_.trustedCa, "Trusted CA", SearchFieldTypeEnum.BOOLEAN),
    CERTIFICATE_PROTOCOL(Resource.CERTIFICATE, null, List.of(Certificate_.protocolAssociation), CertificateProtocolAssociation_.protocol, "Certificate Protocol", SearchFieldTypeEnum.LIST, CertificateProtocol.class),
    HYBRID_CERTIFICATE(Resource.CERTIFICATE, null, null, Certificate_.hybridCertificate, "Hybrid Certificate", SearchFieldTypeEnum.BOOLEAN),
    ARCHIVED(Resource.CERTIFICATE, null, null, Certificate_.archived, "Archived", SearchFieldTypeEnum.BOOLEAN),

    // Cryptographic Key
    CKI_NAME(Resource.CRYPTOGRAPHIC_KEY, null, null, CryptographicKeyItem_.name, "Name", SearchFieldTypeEnum.STRING),
    CKI_TYPE(Resource.CRYPTOGRAPHIC_KEY, null, null, CryptographicKeyItem_.type, "Key type", SearchFieldTypeEnum.LIST, KeyType.class, null, false, null),
    CKI_FORMAT(Resource.CRYPTOGRAPHIC_KEY, null, null, CryptographicKeyItem_.format, "Key format", SearchFieldTypeEnum.LIST, KeyFormat.class, null, false, null),
    CKI_STATE(Resource.CRYPTOGRAPHIC_KEY, null, null, CryptographicKeyItem_.state, "State", SearchFieldTypeEnum.LIST, KeyState.class, null, false, null),
    CKI_CRYPTOGRAPHIC_ALGORITHM(Resource.CRYPTOGRAPHIC_KEY, null, null, CryptographicKeyItem_.keyAlgorithm, "Cryptographic algorithm", SearchFieldTypeEnum.LIST, KeyAlgorithm.class, null, false, null),
    CKI_USAGE(Resource.CRYPTOGRAPHIC_KEY, null, null, CryptographicKeyItem_.usage, "Key Usage", SearchFieldTypeEnum.LIST, KeyUsage.class, null, false, null),
    CKI_LENGTH(Resource.CRYPTOGRAPHIC_KEY, null, null, CryptographicKeyItem_.length, "Key Size", SearchFieldTypeEnum.NUMBER),
    CK_TOKEN_PROFILE(Resource.CRYPTOGRAPHIC_KEY, Resource.TOKEN_PROFILE, List.of(CryptographicKeyItem_.key, CryptographicKey_.tokenProfile), TokenProfile_.name, "Token profile", SearchFieldTypeEnum.LIST),
    CK_TOKEN_INSTANCE(Resource.CRYPTOGRAPHIC_KEY, Resource.TOKEN, List.of(CryptographicKeyItem_.key, CryptographicKey_.tokenInstanceReference), TokenInstanceReference_.name, "Token instance", SearchFieldTypeEnum.LIST),
    CK_GROUP(Resource.CRYPTOGRAPHIC_KEY, Resource.GROUP, List.of(CryptographicKeyItem_.key, CryptographicKey_.groups), Group_.name, "Groups", SearchFieldTypeEnum.LIST, null, null, true, null),
    CK_OWNER(Resource.CRYPTOGRAPHIC_KEY, Resource.USER, List.of(CryptographicKeyItem_.key, CryptographicKey_.owner), OwnerAssociation_.ownerUsername, "Owner", SearchFieldTypeEnum.LIST, null, null, true, null),

    // Discovery
    DISCOVERY_NAME(Resource.DISCOVERY, null, null, DiscoveryHistory_.name, "Name", SearchFieldTypeEnum.STRING),
    DISCOVERY_START_TIME(Resource.DISCOVERY, null, null, DiscoveryHistory_.startTime, "Start time", SearchFieldTypeEnum.DATETIME),
    DISCOVERY_END_TIME(Resource.DISCOVERY, null, null, DiscoveryHistory_.endTime, "End time", SearchFieldTypeEnum.DATETIME),
    DISCOVERY_STATUS(Resource.DISCOVERY, null, null, DiscoveryHistory_.status, "Status", SearchFieldTypeEnum.LIST, DiscoveryStatus.class, null, false, null),
    DISCOVERY_TOTAL_CERT_DISCOVERED(Resource.DISCOVERY, null, null, DiscoveryHistory_.totalCertificatesDiscovered, "Total certificate discovered", SearchFieldTypeEnum.NUMBER),
    DISCOVERY_CONNECTOR_NAME(Resource.DISCOVERY, null, null, DiscoveryHistory_.connectorName, "Discovery provider", SearchFieldTypeEnum.LIST),
    DISCOVERY_KIND(Resource.DISCOVERY, null, null, DiscoveryHistory_.kind, "Kind", SearchFieldTypeEnum.STRING),

    // Entity
    ENTITY_NAME(Resource.ENTITY, null, null, EntityInstanceReference_.name, "Name", SearchFieldTypeEnum.STRING),
    ENTITY_CONNECTOR_NAME(Resource.ENTITY, null, null, EntityInstanceReference_.connectorName, "Entity provider", SearchFieldTypeEnum.LIST),
    ENTITY_KIND(Resource.ENTITY, null, null, EntityInstanceReference_.kind, "Kind", SearchFieldTypeEnum.LIST),

    // Location
    LOCATION_NAME(Resource.LOCATION, null, null, Location_.name, "Name", SearchFieldTypeEnum.STRING),
    LOCATION_ENTITY_INSTANCE(Resource.LOCATION, null, null, Location_.entityInstanceName, "Entity instance", SearchFieldTypeEnum.LIST),
    LOCATION_ENABLED(Resource.LOCATION, null, null, Location_.enabled, "Enabled", SearchFieldTypeEnum.BOOLEAN),
    LOCATION_SUPPORT_MULTIPLE_ENTRIES(Resource.LOCATION, null, null, Location_.supportMultipleEntries, "Support multiple entries", SearchFieldTypeEnum.BOOLEAN),
    LOCATION_SUPPORT_KEY_MANAGEMENT(Resource.LOCATION, null, null, Location_.supportKeyManagement, "Support key management", SearchFieldTypeEnum.BOOLEAN),


    // Audit Logs
    AUDIT_LOG_TIMESTAMP(Resource.AUDIT_LOG, null, null, AuditLog_.loggedAt, "Logged at", SearchFieldTypeEnum.DATETIME),
    AUDIT_LOG_MODULE(Resource.AUDIT_LOG, null, null, AuditLog_.module, "Module", SearchFieldTypeEnum.LIST, Module.class),
    AUDIT_LOG_ACTOR_TYPE(Resource.AUDIT_LOG, null, null, AuditLog_.actorType, "Actor type", SearchFieldTypeEnum.LIST, ActorType.class),
    AUDIT_LOG_ACTOR_NAME(Resource.AUDIT_LOG, null, null, AuditLog_.actorName, "Actor name", SearchFieldTypeEnum.STRING),
    AUDIT_LOG_ACTOR_AUTH_METHOD(Resource.AUDIT_LOG, null, null, AuditLog_.actorAuthMethod, "Actor Auth method", SearchFieldTypeEnum.LIST, AuthMethod.class),
    AUDIT_LOG_RESOURCE(Resource.AUDIT_LOG, null, null, AuditLog_.resource, "Resource", SearchFieldTypeEnum.LIST, Resource.class),
    AUDIT_LOG_AFFILIATED_RESOURCE(Resource.AUDIT_LOG, null, null, AuditLog_.affiliatedResource, "Affiliated resource", SearchFieldTypeEnum.LIST, Resource.class),
    AUDIT_LOG_OPERATION(Resource.AUDIT_LOG, null, null, AuditLog_.operation, "Operation", SearchFieldTypeEnum.LIST, Operation.class),
    AUDIT_LOG_OPERATION_RESULT(Resource.AUDIT_LOG, null, null, AuditLog_.operationResult, "Operation result", SearchFieldTypeEnum.LIST, OperationResult.class),
    AUDIT_LOG_SOURCE_IP_ADDRESS(Resource.AUDIT_LOG, null, null, AuditLog_.logRecord, "IP Address", SearchFieldTypeEnum.STRING, new String[]{"source", "ipAddress"}),
    AUDIT_LOG_SOURCE_PATH(Resource.AUDIT_LOG, null, null, AuditLog_.logRecord, "API path", SearchFieldTypeEnum.STRING, new String[]{"source", "path"}),
    AUDIT_LOG_MESSAGE(Resource.AUDIT_LOG, null, null, AuditLog_.message, "Message", SearchFieldTypeEnum.STRING),

    // Scheduled Job
    SCHEDULED_JOB_NAME(Resource.SCHEDULED_JOB, null, null, ScheduledJob_.jobName, "Job Name", SearchFieldTypeEnum.STRING),
    SCHEDULED_JOB_ONE_TIME(Resource.SCHEDULED_JOB, null, null, ScheduledJob_.oneTime, "One Time", SearchFieldTypeEnum.BOOLEAN),
    SCHEDULED_JOB_SYSTEM(Resource.SCHEDULED_JOB, null, null, ScheduledJob_.system, "System", SearchFieldTypeEnum.BOOLEAN),
    SCHEDULED_JOB_CLASS_NAME(Resource.SCHEDULED_JOB, null, null, ScheduledJob_.jobClassName, "Class Name", SearchFieldTypeEnum.LIST),

    // Approval
    APPROVAL_RESOURCE(Resource.APPROVAL, null, null, Approval_.resource, "Resource", SearchFieldTypeEnum.LIST, Resource.class),
    APPROVAL_ACTION(Resource.APPROVAL, null, null, Approval_.action, "Action", SearchFieldTypeEnum.LIST, ResourceAction.class),
    APPROVAL_STATUS(Resource.APPROVAL, null, null, Approval_.status, "Status", SearchFieldTypeEnum.LIST, ApprovalStatusEnum.class),
    APPROVAL_CREATED_AT(Resource.APPROVAL, null, null, Approval_.createdAt, "Created At", SearchFieldTypeEnum.DATETIME),
    APPROVAL_EXPIRY_AT(Resource.APPROVAL, null, null, Approval_.expiryAt, "Expiry At", SearchFieldTypeEnum.DATETIME),
    APPROVAL_CLOSED_AT(Resource.APPROVAL, null, null, Approval_.closedAt, "Closed At", SearchFieldTypeEnum.DATETIME),

    // OID Entry
    OID_ENTRY_OID(Resource.OID, null, null, CustomOidEntry_.oid, "OID", SearchFieldTypeEnum.STRING),
    OID_ENTRY_DISPLAY_NAME(Resource.OID, null, null, CustomOidEntry_.displayName, "Display Name", SearchFieldTypeEnum.STRING),
    OID_ENTRY_CATEGORY(Resource.OID, null, null, CustomOidEntry_.category, "Category", SearchFieldTypeEnum.LIST, OidCategory.class),
    OID_ENTRY_CODE(Resource.OID, null, null, RdnAttributeTypeCustomOidEntry_.code, "Code", SearchFieldTypeEnum.STRING);

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
    private final String[] jsonPath;
    private final Class<? extends IPlatformEnum> enumClass;
    private final boolean settable;
    private final Object expectedValue;

    FilterField(final Resource rootResource, final Resource fieldResource, final List<Attribute> joinAttributes, final Attribute fieldAttribute, final String label, final SearchFieldTypeEnum type) {
        this(rootResource, fieldResource, joinAttributes, fieldAttribute, label, type, null, null, false, null);
    }

    FilterField(final Resource rootResource, final Resource fieldResource, final List<Attribute> joinAttributes, final Attribute fieldAttribute, final String label, final SearchFieldTypeEnum type, String[] jsonPath) {
        this(rootResource, fieldResource, joinAttributes, fieldAttribute, label, type, null, null, false, jsonPath);
    }

    FilterField(final Resource rootResource, final Resource fieldResource, final List<Attribute> joinAttributes, final Attribute fieldAttribute, final String label, final SearchFieldTypeEnum type, final Class<? extends IPlatformEnum> enumClass) {
        this(rootResource, fieldResource, joinAttributes, fieldAttribute, label, type, enumClass, null, false, null);
    }

    FilterField(final Resource rootResource, final Resource fieldResource, final List<Attribute> joinAttributes, final Attribute fieldAttribute, final String label, final SearchFieldTypeEnum type, final Class<? extends IPlatformEnum> enumClass, final Object expectedValue, final boolean settable, final String[] jsonPath) {
        this.rootResource = rootResource;
        this.fieldResource = fieldResource;
        this.joinAttributes = joinAttributes == null ? List.of() : joinAttributes;
        this.fieldAttribute = fieldAttribute;
        this.label = label;
        this.type = type;
        this.jsonPath = jsonPath;
        this.enumClass = enumClass;
        this.settable = settable;
        this.expectedValue = expectedValue;
    }

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

    public String[] getJsonPath() {
        return jsonPath;
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
