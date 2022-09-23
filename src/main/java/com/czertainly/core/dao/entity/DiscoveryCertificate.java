package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.discovery.DiscoveryCertificatesDto;
import com.czertainly.core.util.DtoMapper;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "discovery_certificate")
public class DiscoveryCertificate extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<DiscoveryCertificatesDto> {
    /**
     *
     */
    private static final long serialVersionUID = 9115753988094130017L;

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
    @JoinColumn(name = "certificate_content_id", nullable = false, insertable = false, updatable = false)
    private CertificateContent certificateContent;

    @Column(name = "certificate_content_id", nullable = false)
    private Long certificateContentId ;

    @OneToOne
    @JoinColumn(name = "discovery_uuid", nullable = false, insertable = false, updatable = false)
    private DiscoveryHistory discovery;

    @Column(name = "discovery_uuid", nullable = false)
    private UUID discoveryUuid;

    @Override
    public DiscoveryCertificatesDto mapToDto() {
        DiscoveryCertificatesDto dto = new DiscoveryCertificatesDto();
        dto.setUuid(uuid.toString());
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
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("commonName", commonName).append("serialNumber", serialNumber).toString();
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
        this.certificateContentId = certificateContent.getId();
    }

    public DiscoveryHistory getDiscovery() {
        return discovery;
    }

    public void setDiscovery(DiscoveryHistory discovery) {
        this.discovery = discovery;
        this.discoveryUuid = discovery.getUuid();
    }

    public Long getCertificateContentId() {
        return certificateContentId;
    }

    public void setCertificateContentId(Long certificateContentId) {
        this.certificateContentId = certificateContentId;
    }

    public UUID getDiscoveryUuid() {
        return discoveryUuid;
    }

    public void setDiscoveryUuid(String discoveryUuid) {
        this.discoveryUuid = UUID.fromString(discoveryUuid);
    }
}
