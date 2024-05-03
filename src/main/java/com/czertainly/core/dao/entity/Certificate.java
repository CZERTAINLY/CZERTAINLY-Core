package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.raprofile.SimplifiedRaProfileDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.core.certificate.*;
import com.czertainly.api.model.core.compliance.ComplianceStatus;
import com.czertainly.api.model.core.cryptography.key.KeyState;
import com.czertainly.api.model.core.enums.CertificateRequestFormat;
import com.czertainly.core.util.*;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.hibernate.annotations.Where;
import org.hibernate.annotations.WhereJoinTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Entity
@Table(name = "certificate")
public class Certificate extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<CertificateDetailDto> {
    private static final String EMPTY_COMMON_NAME = "<empty>";

    private static final long serialVersionUID = -3048734620156664554L;

    private static final Logger logger = LoggerFactory.getLogger(Certificate.class);

    @Column(name = "common_name")
    private String commonName;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "issuer_common_name")
    private String issuerCommonName;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_content_id", insertable = false, updatable = false)
    private CertificateContent certificateContent;

    @Column(name = "certificate_content_id")
    private Long certificateContentId;

    @Column(name = "issuer_dn")
    private String issuerDn;

    @Column(name = "issuer_dn_normalized")
    private String issuerDnNormalized;

    @Column(name = "subject_dn")
    private String subjectDn;

    @Column(name = "subject_dn_normalized")
    private String subjectDnNormalized;

    @Column(name = "not_before")
    private Date notBefore;

    @Column(name = "not_after")
    private Date notAfter;

    @Column(name = "public_key_algorithm")
    private String publicKeyAlgorithm;

    @Column(name = "signature_algorithm")
    private String signatureAlgorithm;

    @Column(name = "extended_key_usage")
    private String extendedKeyUsage;

    @Column(name = "key_usage")
    private String keyUsage;

    @Column(name = "basic_constraints")
    private String basicConstraints;

    @Column(name = "state")
    @Enumerated(EnumType.STRING)
    private CertificateState state;

    @Column(name = "validation_status")
    @Enumerated(EnumType.STRING)
    private CertificateValidationStatus validationStatus;

    @Column(name = "fingerprint")
    private String fingerprint;

    @Column(name = "public_key_fingerprint")
    private String publicKeyFingerprint;

    @Column(name = "subject_alternative_names")
    private String subjectAlternativeNames;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ra_profile_uuid", insertable = false, updatable = false)
    private RaProfile raProfile;

    @Column(name = "ra_profile_uuid")
    private UUID raProfileUuid;

//    @OneToMany(fetch = FetchType.LAZY)
//    @JoinTable(name = "resource_object_association", joinColumns = @JoinColumn(name = "object_uuid"), inverseJoinColumns = @JoinColumn(name = "group_uuid"))
//    @WhereJoinTable(clause = "resource = 'CERTIFICATE' AND group_uuid IS NOT NULL")
//    private Set<Group> groups;

    @ManyToMany(fetch = FetchType.LAZY, targetEntity = Group.class)
    @JoinTable(name = "resource_object_association", joinColumns = @JoinColumn(name = "object_uuid", insertable = false, updatable = false), inverseJoinColumns = @JoinColumn(name = "group_uuid", insertable = false, updatable = false))
    @WhereJoinTable(clause = "resource = 'CERTIFICATE' AND type = 'GROUP'")
    private Set<Group> groups = new HashSet<>();

//    @OneToMany(fetch = FetchType.LAZY)
//    @JoinColumn(name = "object_uuid")
//    @Where(clause = "resource = 'CERTIFICATE'")
//    private List<GroupAssociation> groups;

//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinTable(name = "resource_object_association", joinColumns = @JoinColumn(name = "object_uuid"))
//    @WhereJoinTable(clause = "resource = CERTIFICATE AND owner_uuid IS NOT NULL")
//    @JoinFormula("(SELECT r.uuid FROM resource_object_association r WHERE r.object_uuid = uuid AND r.resource = 'CERTIFICATE' AND owner_uuid IS NOT NULL)")

    //    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "object_uuid", referencedColumnName = "uuid", insertable = false, updatable = false)
//    @Where(clause = "resource = 'CERTIFICATE' AND owner_uuid IS NOT NULL")
//    @Transient
//    private NameAndUuidDto owner;

//    @OneToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "object_uuid", referencedColumnName = "uuid", insertable = false, updatable = false)
//    @Where(clause = "resource = 'CERTIFICATE'")
//    private OwnerAssociation ownerAssociation;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "object_uuid", insertable = false, updatable = false)
    @Where(clause = "resource = 'CERTIFICATE'")
    private List<OwnerAssociation> owners = new ArrayList<>();

    @Column(name = "status_validation_timestamp")
    private LocalDateTime statusValidationTimestamp;

    @OneToMany(
            mappedBy = "certificate",
            fetch = FetchType.LAZY
            //orphanRemoval = true
    )
    @JsonBackReference
    private Set<CertificateLocation> locations = new HashSet<>();

    @Column(name = "key_size")
    private Integer keySize;

    @Column(name = "certificate_type")
    @Enumerated(EnumType.STRING)
    private CertificateType certificateType;

    @Column(name = "issuer_serial_number")
    private String issuerSerialNumber;

    @Column(name = "issuer_certificate_uuid")
    private UUID issuerCertificateUuid;

    @Column(name = "certificate_validation_result", length = 100000)
    private String certificateValidationResult;

    @Column(name = "compliance_result")
    private String complianceResult;

    @Column(name = "compliance_status")
    @Enumerated(EnumType.STRING)
    private ComplianceStatus complianceStatus;

    @JsonBackReference
    @OneToMany(mappedBy = "certificate", fetch = FetchType.LAZY)
    private Set<CertificateEventHistory> eventHistories = new HashSet<>();

    @Column(name = "user_uuid")
    private UUID userUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "key_uuid", insertable = false, updatable = false)
    private CryptographicKey key;

    @Column(name = "key_uuid")
    private UUID keyUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_request_uuid", insertable = false, updatable = false)
    private CertificateRequestEntity certificateRequestEntity;

    @Column(name = "certificate_request_uuid")
    private UUID certificateRequestUuid;

    @Column(name = "source_certificate_uuid")
    private UUID sourceCertificateUuid;

    @Column(name = "trusted_ca")
    private Boolean trustedCa;

    @Override
    public CertificateDetailDto mapToDto() {
        final CertificateDetailDto dto = new CertificateDetailDto();
        dto.setCommonName(commonName != null ? commonName : EMPTY_COMMON_NAME);
        dto.setIssuerCommonName(getIssuerCommonNameToDto());
        if (certificateContent != null) {
            dto.setCertificateContent(certificateContent.getContent());
            dto.setIssuerDn(issuerDn);
            dto.setNotBefore(notBefore);
            dto.setNotAfter(notAfter);
            dto.setBasicConstraints(basicConstraints);
            dto.setExtendedKeyUsage(MetaDefinitions.deserializeArrayString(extendedKeyUsage));
            dto.setKeyUsage(MetaDefinitions.deserializeArrayString(keyUsage));
            dto.setFingerprint(fingerprint);
            dto.setSubjectAlternativeNames(MetaDefinitions.deserialize(subjectAlternativeNames));
            dto.setIssuerSerialNumber(issuerSerialNumber);
            dto.setSerialNumber(serialNumber);
        }
        dto.setSourceCertificateUuid(sourceCertificateUuid);
        dto.setSubjectDn(subjectDn);
        dto.setPublicKeyAlgorithm(CertificateUtil.getAlgorithmFriendlyName(publicKeyAlgorithm));
        dto.setSignatureAlgorithm(CertificateUtil.getAlgorithmFriendlyName(signatureAlgorithm));
        dto.setKeySize(keySize);
        dto.setUuid(uuid.toString());
        dto.setState(state);
        dto.setValidationStatus(validationStatus);
        dto.setCertificateType(certificateType);
        dto.setTrustedCa(trustedCa);
        if (issuerCertificateUuid != null) dto.setIssuerCertificateUuid(issuerCertificateUuid.toString());
        if (getOwner() != null) {
            dto.setOwnerUuid(getOwner().getUuid());
            dto.setOwner(getOwner().getName());
        }

        /*
         * Result for the compliance check of a certificate is stored in the database in the form of List of Rule IDs.
         * When the details of the certificate is requested, the Service will transform the result into the user understandable
         * format and send it. It is not moved into the mapToDto function, as the computation involves other repositories
         * like complianceRules etc., So only the overall status of the compliance will be set in the mapToDto function
         */
        dto.setComplianceStatus(complianceStatus);

        if (raProfile != null) {
            SimplifiedRaProfileDto raDto = new SimplifiedRaProfileDto();
            raDto.setName(raProfile.getName());
            raDto.setUuid(raProfile.getUuid().toString());
            raDto.setEnabled(raProfile.getEnabled());
            if (raProfile.getAuthorityInstanceReference() != null) {
                raDto.setAuthorityInstanceUuid(raProfile.getAuthorityInstanceReference().getUuid().toString());
            }
            dto.setRaProfile(raDto);
        }
        if (groups != null) {
            dto.setGroups(groups.stream().map(Group::mapToDto).toList());
//            dto.setGroups(groups.stream().map(g -> g.getGroup().mapToDto()).toList());
        }

        //Check and assign private key availability
        dto.setPrivateKeyAvailability(false);

        if (this.certificateRequestEntity != null) {
            final CertificateRequestDto certificateRequestDto = new CertificateRequestDto();
            certificateRequestDto.setContent(this.certificateRequestEntity.getContent());
            certificateRequestDto.setCertificateType(this.certificateRequestEntity.getCertificateType());
            certificateRequestDto.setCommonName(this.certificateRequestEntity.getCommonName());
            certificateRequestDto.setSubjectDn(this.certificateRequestEntity.getSubjectDn());
            certificateRequestDto.setSignatureAlgorithm(this.certificateRequestEntity.getSignatureAlgorithm());
            certificateRequestDto.setPublicKeyAlgorithm(this.certificateRequestEntity.getPublicKeyAlgorithm());
            certificateRequestDto.setCertificateRequestFormat(this.certificateRequestEntity.getCertificateRequestFormat());
            certificateRequestDto.setSubjectAlternativeNames(MetaDefinitions.deserialize(this.certificateRequestEntity.getSubjectAlternativeNames()));
            dto.setCertificateRequest(certificateRequestDto);
        }
        if (key != null && !key.getItems().isEmpty()) {
            if (!key.getItems()
                    .stream()
                    .filter(
                            item -> item.getType()
                                    .equals(KeyType.PRIVATE_KEY)
                                    && item.getState().equals(KeyState.ACTIVE)
                    ).toList()
                    .isEmpty()
            ) {
                dto.setPrivateKeyAvailability(true);
            }
        }
        if (key != null) dto.setKey(key.mapToDto());
        return dto;
    }

    public CertificateDto mapToListDto() {
        CertificateDto dto = new CertificateDto();
        dto.setCommonName(commonName != null ? commonName : EMPTY_COMMON_NAME);
        dto.setIssuerCommonName(getIssuerCommonNameToDto());
        dto.setSerialNumber(serialNumber);
        dto.setIssuerCommonName(issuerCommonName);
        dto.setIssuerDn(issuerDn);
        dto.setSubjectDn(subjectDn);
        dto.setNotBefore(notBefore);
        dto.setNotAfter(notAfter);
        dto.setPublicKeyAlgorithm(CertificateUtil.getAlgorithmFriendlyName(publicKeyAlgorithm));
        dto.setSignatureAlgorithm(CertificateUtil.getAlgorithmFriendlyName(signatureAlgorithm));
        dto.setKeySize(keySize);
        dto.setUuid(uuid.toString());
        dto.setState(state);
        dto.setValidationStatus(validationStatus);
        dto.setFingerprint(fingerprint);
        dto.setTrustedCa(trustedCa);
        if (issuerCertificateUuid != null) dto.setIssuerCertificateUuid(issuerCertificateUuid.toString());
        if (getOwner() != null) {
            dto.setOwnerUuid(getOwner().getUuid());
            dto.setOwner(getOwner().getName());
        }
        dto.setCertificateType(certificateType);
        dto.setIssuerSerialNumber(issuerSerialNumber);
        /**
         * Result for the compliance check of a certificate is stored in the database in the form of List of Rule IDs.
         * When the details of the certificate is requested, the Service will transform the result into the user understandable
         * format and send it. It is not moved into the mapToDto function, as the computation involves other repositories
         * like complianceRules etc., So only the overall status of the compliance will be set in the mapToDto function
         */
        dto.setComplianceStatus(complianceStatus);

        if (raProfile != null) {
            SimplifiedRaProfileDto raDto = new SimplifiedRaProfileDto();
            raDto.setName(raProfile.getName());
            raDto.setUuid(raProfile.getUuid().toString());
            raDto.setEnabled(raProfile.getEnabled());
            if (raProfile.getAuthorityInstanceReference() != null) {
                raDto.setAuthorityInstanceUuid(raProfile.getAuthorityInstanceReference().getUuid().toString());
            }
            dto.setRaProfile(raDto);
        }
        if (groups != null) {
            dto.setGroups(groups.stream().map(Group::mapToDto).toList());
//            dto.setGroups(groups.stream().map(g -> g.getGroup().mapToDto()).toList());
        }

        //Check and assign private key availability
        dto.setPrivateKeyAvailability(false);
        if (key != null && !key.getItems().isEmpty()) {
            if (!key.getItems()
                    .stream()
                    .filter(
                            item -> item.getType()
                                    .equals(KeyType.PRIVATE_KEY)
                                    && item.getState().equals(KeyState.ACTIVE)
                    ).toList()
                    .isEmpty()
            ) {
                dto.setPrivateKeyAvailability(true);
            }
        }
        return dto;
    }

    public CertificateRequestEntity prepareCertificateRequest(final CertificateRequestFormat certificateRequestFormat) {
        final CertificateRequestEntity certificateRequestEntity = new CertificateRequestEntity();
        certificateRequestEntity.setCertificateType(this.certificateType);
        certificateRequestEntity.setKeyUsage(this.keyUsage);
        certificateRequestEntity.setCommonName(this.commonName);
        certificateRequestEntity.setPublicKeyAlgorithm(this.publicKeyAlgorithm);
        certificateRequestEntity.setSignatureAlgorithm(this.signatureAlgorithm);
        certificateRequestEntity.setSubjectAlternativeNames(this.subjectAlternativeNames);
        certificateRequestEntity.setSubjectDn(this.subjectDn);
        certificateRequestEntity.setCertificateRequestFormat(certificateRequestFormat);
        return certificateRequestEntity;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).append("uuid", uuid)
                .append("commonName", commonName).append("serialNumber", serialNumber).toString();
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public String getIssuerCommonName() {
        return issuerCommonName;
    }

    public void setIssuerCommonName(String issuerCommonName) {
        this.issuerCommonName = issuerCommonName;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public Date getNotBefore() {
        return notBefore;
    }

    public void setNotBefore(Date notBefore) {
        this.notBefore = notBefore;
    }

    public Date getNotAfter() {
        return notAfter;
    }

    public void setNotAfter(Date notAfter) {
        this.notAfter = notAfter;
    }

    public String getPublicKeyAlgorithm() {
        return publicKeyAlgorithm;
    }

    public void setPublicKeyAlgorithm(String publicKeyAlgorithm) {
        this.publicKeyAlgorithm = publicKeyAlgorithm;
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    public void setSignatureAlgorithm(String signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
    }

    public String getBasicConstraints() {
        return basicConstraints;
    }

    public void setBasicConstraints(String basicConstraints) {
        this.basicConstraints = basicConstraints;
    }

    public String getIssuerDn() {
        return issuerDn;
    }

    public void setIssuerDn(String issuerDn) {
        this.issuerDn = issuerDn;
    }

    public String getSubjectDn() {
        return subjectDn;
    }

    public void setSubjectDn(String subjectDn) {
        this.subjectDn = subjectDn;
    }

    public String getIssuerDnNormalized() {
        return issuerDnNormalized;
    }

    public void setIssuerDnNormalized(String issuerDnNormalized) {
        this.issuerDnNormalized = issuerDnNormalized;
    }

    public String getSubjectDnNormalized() {
        return subjectDnNormalized;
    }

    public void setSubjectDnNormalized(String subjectDnNormalized) {
        this.subjectDnNormalized = subjectDnNormalized;
    }

    public CertificateContent getCertificateContent() {
        return certificateContent;
    }

    public void setCertificateContent(CertificateContent certificateContent) {
        this.certificateContent = certificateContent;
        if (certificateContent != null) this.certificateContentId = certificateContent.getId();
        else this.certificateContentId = null;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public CertificateState getState() {
        return state;
    }

    public void setState(CertificateState state) {
        this.state = state;
    }

    public CertificateValidationStatus getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(CertificateValidationStatus validationStatus) {
        this.validationStatus = validationStatus;
    }

    public String getExtendedKeyUsage() {
        return extendedKeyUsage;
    }

    public void setExtendedKeyUsage(String extendedKeyUsage) {
        this.extendedKeyUsage = extendedKeyUsage;
    }

    public String getKeyUsage() {
        return keyUsage;
    }

    public void setKeyUsage(String keyUsage) {
        this.keyUsage = keyUsage;
    }

    public String getSubjectAlternativeNames() {
        return subjectAlternativeNames;
    }

    public void setSubjectAlternativeNames(String subjectAlternativeNames) {
        this.subjectAlternativeNames = subjectAlternativeNames;
    }

    public Integer getKeySize() {
        return keySize;
    }

    public void setKeySize(Integer keySize) {
        this.keySize = keySize;
    }

    public CertificateType getCertificateType() {
        return certificateType;
    }

    public void setCertificateType(CertificateType certificateType) {
        this.certificateType = certificateType;
    }

    public String getIssuerSerialNumber() {
        return issuerSerialNumber;
    }

    public void setIssuerSerialNumber(String issuerSerialNumber) {
        this.issuerSerialNumber = issuerSerialNumber;
    }

    public RaProfile getRaProfile() {
        return raProfile;
    }

    public void setRaProfile(RaProfile raProfile) {
        this.raProfile = raProfile;
        if (raProfile != null) this.raProfileUuid = raProfile.getUuid();
        else this.raProfileUuid = null;
    }

    public String getCertificateValidationResult() {
        return certificateValidationResult;
    }

    public void setCertificateValidationResult(String certificateValidationResult) {
        this.certificateValidationResult = certificateValidationResult;
    }

    public Set<CertificateEventHistory> getEventHistories() {
        return eventHistories;
    }

    public void setEventHistories(Set<CertificateEventHistory> eventHistories) {
        this.eventHistories = eventHistories;
    }

    public Set<CertificateLocation> getLocations() {
        return locations;
    }

    public void setLocations(Set<CertificateLocation> locations) {
        this.locations = locations;
    }

    public UUID getRaProfileUuid() {
        return raProfileUuid;
    }

    public void setRaProfileUuid(UUID raProfileUuid) {
        this.raProfileUuid = raProfileUuid;
    }

    public NameAndUuidDto getOwner() {
        return owners == null || owners.isEmpty() ? null : owners.get(0).getOwnerInfo();
    }

    public Set<Group> getGroups() {
        return groups;
//        return groups.stream().map(GroupAssociation::getGroup).collect(Collectors.toSet());
    }

    public void setGroups(Set<Group> groups) {
        this.groups = groups;
    }

    public CertificateComplianceStorageDto getComplianceResult() {
        if (complianceResult == null) {
            return null;
        }
        return (CertificateComplianceStorageDto) SerializationUtil.deserialize(complianceResult, CertificateComplianceStorageDto.class);
    }

    public void setComplianceResult(CertificateComplianceStorageDto complianceResult) {
        this.complianceResult = SerializationUtil.serialize(complianceResult);
    }

    public ComplianceStatus getComplianceStatus() {
        return complianceStatus;
    }

    public void setComplianceStatus(ComplianceStatus complianceStatus) {
        this.complianceStatus = complianceStatus;
    }

    public Long getCertificateContentId() {
        return certificateContentId;
    }

    public void setCertificateContentId(Long certificateContentId) {
        this.certificateContentId = certificateContentId;
    }

    public UUID getUserUuid() {
        return userUuid;
    }

    public void setUserUuid(UUID userUuid) {
        this.userUuid = userUuid;
    }

    public CryptographicKey getKey() {
        return key;
    }

    public void setKey(CryptographicKey key) {
        this.key = key;
        if (key != null) this.keyUuid = uuid;
    }

    public UUID getKeyUuid() {
        return keyUuid;
    }

    public void setKeyUuid(UUID keyUuid) {
        this.keyUuid = keyUuid;
    }

    public String getPublicKeyFingerprint() {
        return publicKeyFingerprint;
    }

    public void setPublicKeyFingerprint(String publicKeyFingerprint) {
        this.publicKeyFingerprint = publicKeyFingerprint;
    }

    public LocalDateTime getStatusValidationTimestamp() {
        return statusValidationTimestamp;
    }

    public void setStatusValidationTimestamp(LocalDateTime statusValidationTimestamp) {
        this.statusValidationTimestamp = statusValidationTimestamp;
    }

    public Long getValidity() {
        return TimeUnit.DAYS.convert(Math.abs(notAfter.getTime() - notBefore.getTime()), TimeUnit.MILLISECONDS);
    }

    public Long getExpiryInDays() {
        return TimeUnit.DAYS.convert(Math.abs(notAfter.getTime() - new Date().getTime()), TimeUnit.MILLISECONDS);
    }

    public CertificateRequestEntity getCertificateRequest() {
        return certificateRequestEntity;
    }

    public void setCertificateRequest(CertificateRequestEntity certificateRequestEntity) {
        this.certificateRequestEntity = certificateRequestEntity;
    }

    public UUID getCertificateRequestUuid() {
        return certificateRequestUuid;
    }

    public void setCertificateRequestUuid(UUID certificateRequestUuid) {
        this.certificateRequestUuid = certificateRequestUuid;
    }


    public UUID getSourceCertificateUuid() {
        return sourceCertificateUuid;
    }

    public void setSourceCertificateUuid(UUID sourceCertificateUuid) {
        this.sourceCertificateUuid = sourceCertificateUuid;
    }

    public UUID getIssuerCertificateUuid() {
        return issuerCertificateUuid;
    }

    public void setIssuerCertificateUuid(UUID issuerCertificateUuid) {
        this.issuerCertificateUuid = issuerCertificateUuid;
    }

    private String getIssuerCommonNameToDto() {
        if (issuerCommonName != null) {
            return issuerCommonName;
        } else if (issuerCertificateUuid != null) {
            return EMPTY_COMMON_NAME;
        }
        return null;
    }

    public void setTrustedCa(boolean trustedCa) {
        this.trustedCa = trustedCa;
    }

    public Boolean getTrustedCa() {
        return trustedCa;
    }
}
