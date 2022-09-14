package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.raprofile.SimplifiedRaProfileDto;
import com.czertainly.api.model.core.certificate.CertificateComplianceStorageDto;
import com.czertainly.api.model.core.certificate.CertificateDto;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.compliance.ComplianceStatus;
import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.MetaDefinitions;
import com.czertainly.core.util.SerializationUtil;
import com.fasterxml.jackson.annotation.JsonBackReference;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "certificate")
public class Certificate extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<CertificateDto> {

    private static final long serialVersionUID = -3048734620156664554L;

    private static final Logger logger = LoggerFactory.getLogger(Certificate.class);

    @Column(name = "common_name")
    private String commonName;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "issuer_common_name")
    private String issuerCommonName;

    @OneToOne
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

    @Column(name = "meta")
    private String meta;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private CertificateStatus status;

    @Column(name = "fingerprint")
    private String fingerprint;

    @Column(name = "subject_alternative_names")
    private String subjectAlternativeNames;

    @ManyToOne()
    @JoinColumn(name = "ra_profile_uuid", insertable = false, updatable = false)
    private RaProfile raProfile;

    @Column(name = "ra_profile_uuid")
    private UUID raProfileUuid;

    @ManyToOne
    @JoinColumn(name = "group_uuid", insertable = false, updatable = false)
    private CertificateGroup group;

    @Column(name = "group_uuid")
    private UUID groupUuid;

    @OneToMany(
            mappedBy = "certificate",
            cascade = CascadeType.ALL
            //orphanRemoval = true
    )
    @JsonBackReference
    private Set<CertificateLocation> locations = new HashSet<>();

    @Column(name = "owner")
    private String owner;

    @Column(name = "key_size")
    private Integer keySize;

    @Column(name = "certificate_type")
    @Enumerated(EnumType.STRING)
    private CertificateType certificateType;

    @Column(name = "issuer_serial_number")
    private String issuerSerialNumber;

    @Column(name = "certificate_validation_result", length = 100000)
    private String certificateValidationResult;

    @Column( name = "compliance_result")
    private String complianceResult;

    @Column(name = "compliance_status")
    @Enumerated(EnumType.STRING)
    private ComplianceStatus complianceStatus;

    @JsonBackReference
    @OneToMany(mappedBy = "certificate")
    private Set<CertificateEventHistory> eventHistories = new HashSet<>();

    @Override
    public CertificateDto mapToDto() {
        CertificateDto dto = new CertificateDto();
        dto.setCommonName(commonName);
        dto.setSerialNumber(serialNumber);
        dto.setIssuerCommonName(issuerCommonName);
        dto.setCertificateContent(certificateContent.getContent());
        dto.setIssuerDn(issuerDn);
        dto.setSubjectDn(subjectDn);
        dto.setNotBefore(notBefore);
        dto.setNotAfter(notAfter);
        dto.setPublicKeyAlgorithm(publicKeyAlgorithm);
        dto.setSignatureAlgorithm(signatureAlgorithm);
        dto.setKeySize(keySize);
        dto.setBasicConstraints(basicConstraints);
        dto.setExtendedKeyUsage(MetaDefinitions.deserializeArrayString(extendedKeyUsage));
        dto.setKeyUsage(MetaDefinitions.deserializeArrayString(keyUsage));
        dto.setUuid(uuid.toString());
        dto.setStatus(status);
        dto.setFingerprint(fingerprint);
        dto.setMeta(MetaDefinitions.deserialize(meta));
        dto.setSubjectAlternativeNames(MetaDefinitions.deserialize(subjectAlternativeNames));
        dto.setOwner(owner);
        dto.setCertificateType(certificateType);
        dto.setIssuerSerialNumber(issuerSerialNumber);
        /**
         * Result for the compliance check of a certificate is stored in the database in the form of List of Rule IDs.
         * When the details of the certificate is requested, the Service will transform the result into the user understandable
         * format and send it. It is not moved into the mapToDto function, as the computation involves other repositories
         * like complainceRules etc., So only the overall status of the compliance will be set in the mapToDto function
         */
        dto.setComplianceStatus(complianceStatus);

        if (raProfile != null) {
            SimplifiedRaProfileDto raDto = new SimplifiedRaProfileDto();
            raDto.setName(raProfile.getName());
            raDto.setUuid(raProfile.getUuid().toString());
            raDto.setEnabled(raProfile.getEnabled());
            if(raProfile.getAuthorityInstanceReference() != null) {
                raDto.setAuthorityInstanceUuid(raProfile.getAuthorityInstanceReference().getUuid().toString());
            }
            dto.setRaProfile(raDto);
        }
        if (group != null) {
            dto.setGroup(group.mapToDto());
        }
//        if (locations != null) {
//            dto.setLocations(locations.mapToDto());
//        }
        return dto;
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
        if(certificateContent != null) this.certificateContentId = certificateContent.getId();
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getMeta() {
        return meta;
    }

    public void setMeta(String meta) {
        this.meta = meta;
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
        if(raProfile != null) this.raProfileUuid = raProfile.getUuid();
    }

    public CertificateGroup getGroup() {
        return group;
    }

    public void setGroup(CertificateGroup group) {
        this.group = group;
        if(group != null) this.groupUuid = group.getUuid();
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
        if(complianceResult == null){
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
}
