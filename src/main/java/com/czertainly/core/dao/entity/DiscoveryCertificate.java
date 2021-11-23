package com.czertainly.core.dao.entity;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.czertainly.core.util.DtoMapper;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.czertainly.api.model.discovery.DiscoveryCertificatesDto;

@Entity
@Table(name = "discovery_certificate")
public class DiscoveryCertificate extends Audited implements Serializable, DtoMapper<DiscoveryCertificatesDto> {
    /**
     *
     */
    private static final long serialVersionUID = 9115753988094130017L;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "discovery_certificate_seq")
    @SequenceGenerator(name = "discovery_certificate_seq", sequenceName = "discovery_certificate_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "common_name")
    private String commonName;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "issuer_common_name")
    private String issuerCommonName;

    @Column(name = "not_before")
    private Date notBefore;

    @Column(name = "not_after")
    private Date notAfter;

    @OneToOne
    @JoinColumn(name = "certificate_content_id", nullable = false)
    private CertificateContent certificateContent;

    @OneToOne
    @JoinColumn(name = "discovery_uuid", nullable = false)
    private DiscoveryHistory discovery;

    @Override
    public DiscoveryCertificatesDto mapToDto() {
        DiscoveryCertificatesDto dto = new DiscoveryCertificatesDto();
        dto.setId(id);
        dto.setUuid(uuid);
        dto.setCommonName(commonName);
        dto.setSerialNumber(serialNumber);
        dto.setIssuerCommonName(issuerCommonName);
        dto.setNotBefore(notBefore);
        dto.setNotAfter(notAfter);
        dto.setCertificateContent(certificateContent.getContent());
        dto.setFingerprint(certificateContent.getFingerprint());
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

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getIssuerCommonName() {
        return issuerCommonName;
    }

    public void setIssuerCommonName(String issuerCommonName) {
        this.issuerCommonName = issuerCommonName;
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

    public CertificateContent getCertificateContent() {
        return certificateContent;
    }

    public void setCertificateContent(CertificateContent certificateContent) {
        this.certificateContent = certificateContent;
    }

    public DiscoveryHistory getDiscovery() {
        return discovery;
    }

    public void setDiscovery(DiscoveryHistory discovery) {
        this.discovery = discovery;
    }
}
