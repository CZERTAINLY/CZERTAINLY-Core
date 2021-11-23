package com.czertainly.core.dao.entity;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.MetaDefinitions;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.czertainly.api.model.discovery.CertificateDto;
import com.czertainly.api.model.discovery.CertificateStatus;
import com.czertainly.api.model.discovery.CertificateType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Entity
@Table(name = "certificate")
public class Certificate extends Audited implements Serializable, DtoMapper<CertificateDto> {

    private static final long serialVersionUID = -3048734620156664554L;

    private static final Logger logger = LoggerFactory.getLogger(Certificate.class);

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "certificate_seq")
    @SequenceGenerator(name = "certificate_seq", sequenceName = "certificate_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "common_name")
    private String commonName;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "issuer_common_name")
    private String issuerCommonName;

    @OneToOne
    @JoinColumn(name = "certificate_content_id")
    private CertificateContent certificateContent;

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
    @JoinColumn(name = "ra_profile_id")
    private RaProfile raProfile;

    @ManyToOne
    @JoinColumn(name = "group_id")
    private CertificateGroup group;

    @ManyToOne()
    @JoinColumn(name = "entity_id")
    private CertificateEntity entity;

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

    @Override
    public CertificateDto mapToDto() {
        CertificateDto dto = new CertificateDto();
        dto.setId(id);
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
        dto.setUuid(uuid);
        dto.setStatus(status);
        dto.setFingerprint(fingerprint);
        dto.setMeta(MetaDefinitions.deserialize(meta));
        dto.setSubjectAlternativeNames(MetaDefinitions.deserialize(subjectAlternativeNames));
        dto.setOwner(owner);
        dto.setCertificateType(certificateType);
        dto.setIssuerSerialNumber(issuerSerialNumber);
        if (raProfile != null) {
            dto.setRaProfile(raProfile.mapToDto());
        }
        if (group != null) {
            dto.setGroup(group.mapToDto());
        }
        if (entity != null) {
            dto.setEntity(entity.mapToDto());
        }
        try {
            dto.setCertificateValidationResult(MetaDefinitions.deserializeValidation(certificateValidationResult));
        }catch (IllegalStateException e){
            logger.error(e.getMessage());
            logger.debug(dto.toString());
        }
        return dto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).append("id", id)
                .append("commonName", commonName).append("serialNumber", serialNumber).toString();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCommonName() {
        return commonName;
    }

    public String getIssuerCommonName() {
        return issuerCommonName;
    }

    public void setIssuerCommonName(String issuerCommonName) {
        this.issuerCommonName = issuerCommonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
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
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public void setMeta(String meta) {
        this.meta = meta;
    }

    public String getMeta() {
        return meta;
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
    }

    public CertificateGroup getGroup() {
        return group;
    }

    public void setGroup(CertificateGroup group) {
        this.group = group;
    }

    public CertificateEntity getEntity() {
        return entity;
    }

    public void setEntity(CertificateEntity entity) {
        this.entity = entity;
    }

    public String getCertificateValidationResult() {
        return certificateValidationResult;
    }

    public void setCertificateValidationResult(String certificateValidationResult) {
        this.certificateValidationResult = certificateValidationResult;
    }

}
