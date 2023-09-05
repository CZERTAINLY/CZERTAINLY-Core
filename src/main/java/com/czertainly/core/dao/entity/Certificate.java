package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.raprofile.SimplifiedRaProfileDto;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Entity
@Table(name = "certificate")
public class Certificate extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<CertificateDetailDto> {

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

    @Column(name = "subject_dn")
    private String subjectDn;

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

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private CertificateStatus status;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_uuid", insertable = false, updatable = false)
    private Group group;

    @Column(name = "group_uuid")
    private UUID groupUuid;

    @Column(name = "status_validation_timestamp")
    private LocalDateTime statusValidationTimestamp;

    @OneToMany(
            mappedBy = "certificate",
            fetch = FetchType.LAZY
            //orphanRemoval = true
    )
    @JsonBackReference
    private Set<CertificateLocation> locations = new HashSet<>();

    @Column(name = "owner")
    private String owner;

    @Column(name = "owner_uuid")
    private UUID ownerUuid;

    @Column(name = "key_size")
    private Integer keySize;

    @Column(name = "certificate_type")
    @Enumerated(EnumType.STRING)
    private CertificateType certificateType;

    @Column(name = "issuer_serial_number")
    private String issuerSerialNumber;

    @Column(name = "issuer_certificate_uuid")
    private String issuerCertificateUuid;


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
    private CertificateRequest certificateRequest;

    @Column(name = "certificate_request_uuid")
    private UUID certificateRequestUuid;

    @Column(name = "source_certificate_uuid")
    private UUID sourceCertificateUuid;

    @Column(name = "issue_attributes")
    private String issueAttributes;

    @Column(name = "revoke_attributes")
    private String revokeAttributes;

    @Override
    public CertificateDetailDto mapToDto() {
        final CertificateDetailDto dto = new CertificateDetailDto();
        dto.setCommonName(commonName);
        dto.setIssuerCommonName(issuerCommonName);
        if (!status.equals(CertificateStatus.NEW) && !status.equals(CertificateStatus.REJECTED)) {
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
        dto.setStatus(status);
        dto.setCertificateType(certificateType);
        dto.setOwner(owner);
        dto.setIssuerCertificateUuid(issuerCertificateUuid);
        if (ownerUuid != null) dto.setOwnerUuid(ownerUuid.toString());

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
        if (group != null) {
            dto.setGroup(group.mapToDto());
        }

        //Check and assign private key availability
        dto.setPrivateKeyAvailability(false);

        if (this.certificateRequest != null) {
            final CertificateRequestDto certificateRequestDto = new CertificateRequestDto();
            certificateRequestDto.setContent(this.certificateRequest.getContent());
            certificateRequestDto.setAttributes(
                    AttributeDefinitionUtils.getResponseAttributes(this.certificateRequest.getAttributes())
            );
            certificateRequestDto.setSignatureAttributes(
                    AttributeDefinitionUtils.getResponseAttributes(this.certificateRequest.getSignatureAttributes())
            );
            certificateRequestDto.setCertificateType(this.certificateRequest.getCertificateType());
            certificateRequestDto.setCommonName(this.certificateRequest.getCommonName());
            certificateRequestDto.setSubjectDn(this.certificateRequest.getSubjectDn());
            certificateRequestDto.setSignatureAlgorithm(this.certificateRequest.getSignatureAlgorithm());
            certificateRequestDto.setPublicKeyAlgorithm(this.certificateRequest.getPublicKeyAlgorithm());
            certificateRequestDto.setCertificateRequestFormat(this.certificateRequest.getCertificateRequestFormat());
            certificateRequestDto.setSubjectAlternativeNames(MetaDefinitions.deserialize(this.certificateRequest.getSubjectAlternativeNames()));
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
        dto.setIssueAttributes(AttributeDefinitionUtils.getResponseAttributes(AttributeDefinitionUtils.deserialize(issueAttributes, DataAttribute.class)));
        dto.setRevokeAttributes(AttributeDefinitionUtils.getResponseAttributes(AttributeDefinitionUtils.deserialize(revokeAttributes, DataAttribute.class)));
        return dto;
    }

    public CertificateDto mapToListDto() {
        CertificateDto dto = new CertificateDto();
        dto.setCommonName(commonName);
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
        dto.setStatus(status);
        dto.setFingerprint(fingerprint);
        dto.setOwner(owner);
        dto.setIssuerCertificateUuid(issuerCertificateUuid);
        if (ownerUuid != null) dto.setOwnerUuid(ownerUuid.toString());
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
        if (group != null) {
            dto.setGroup(group.mapToDto());
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

    public CertificateRequest prepareCertificateRequest(final CertificateRequestFormat certificateRequestFormat) {
        final CertificateRequest certificateRequest = new CertificateRequest();
        certificateRequest.setCertificateType(this.certificateType);
        certificateRequest.setKeyUsage(this.keyUsage);
        certificateRequest.setCommonName(this.commonName);
        certificateRequest.setPublicKeyAlgorithm(this.publicKeyAlgorithm);
        certificateRequest.setSignatureAlgorithm(this.signatureAlgorithm);
        certificateRequest.setSubjectAlternativeNames(this.subjectAlternativeNames);
        certificateRequest.setSubjectDn(this.subjectDn);
        certificateRequest.setCertificateRequestFormat(certificateRequestFormat);
        return certificateRequest;
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

    public CertificateStatus getStatus() {
        return status;
    }

    public void setStatus(CertificateStatus status) {
        this.status = status;
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

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
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

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
        if (group != null) this.groupUuid = group.getUuid();
        else this.groupUuid = null;
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

    public UUID getGroupUuid() {
        return groupUuid;
    }

    public void setGroupUuid(UUID groupId) {
        this.groupUuid = groupId;
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

    public CertificateRequest getCertificateRequest() {
        return certificateRequest;
    }

    public void setCertificateRequest(CertificateRequest certificateRequest) {
        this.certificateRequest = certificateRequest;
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

    public String getIssueAttributes() {
        return issueAttributes;
    }

    public void setIssueAttributes(String issueAttributes) {
        this.issueAttributes = issueAttributes;
    }

    public String getIssuerCertificateUuid() {
        return issuerCertificateUuid;
    }

    public void setIssuerCertificateUuid(String issuerCertificateUuid) {
        this.issuerCertificateUuid = issuerCertificateUuid;
    }


    public String getRevokeAttributes() {
        return revokeAttributes;
    }

    public void setRevokeAttributes(String revokeAttributes) {
        this.revokeAttributes = revokeAttributes;
    }
}
