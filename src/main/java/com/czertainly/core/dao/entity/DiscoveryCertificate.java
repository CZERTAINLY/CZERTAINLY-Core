package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.attribute.v2.MetadataAttribute;
import com.czertainly.api.model.core.discovery.DiscoveryCertificateDto;
import com.czertainly.core.util.DtoMapper;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "discovery_certificate")
public class DiscoveryCertificate extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<DiscoveryCertificateDto> {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_content_id", nullable = false, insertable = false, updatable = false)
    private CertificateContent certificateContent;

    @Column(name = "certificate_content_id", nullable = false)
    private Long certificateContentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discovery_uuid", nullable = false, insertable = false, updatable = false)
    private DiscoveryHistory discovery;

    @Column(name = "discovery_uuid", nullable = false)
    private UUID discoveryUuid;

    @Column(name = "newly_discovered", nullable = false)
    private boolean newlyDiscovered;

    @Column(name = "meta", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<MetadataAttribute> meta;

    @Override
    public DiscoveryCertificateDto mapToDto() {
        DiscoveryCertificateDto dto = new DiscoveryCertificateDto();
        dto.setUuid(uuid.toString());
        dto.setCommonName(commonName);
        dto.setSerialNumber(serialNumber);
        dto.setIssuerCommonName(issuerCommonName);
        dto.setNotBefore(notBefore);
        dto.setNotAfter(notAfter);
        dto.setCertificateContent(certificateContent.getContent());
        dto.setFingerprint(certificateContent.getFingerprint());
        dto.setNewlyDiscovered(newlyDiscovered);
        // Certificate Inventory UUID can be obtained from the content table since it has relation to the certificate.
        // If the certificate is deleted from the inventory and the history is not deleted, then the content remains and
        // the certificate becomes null. Also, the Certificate Content is unique for each certificate and the certificate
        // inventory does not contain duplicate data. Hence, a single content will be mapped to a single certificate using
        // One-to-One relation
        if (certificateContent.getCertificate() != null) {
            dto.setInventoryUuid(certificateContent.getCertificate().getUuid().toString());
        }
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

    public boolean isNewlyDiscovered() {
        return newlyDiscovered;
    }

    public void setNewlyDiscovered(boolean newlyDiscovered) {
        this.newlyDiscovered = newlyDiscovered;
    }

    public List<MetadataAttribute> getMeta() {
        return meta;
    }

    public void setMeta(List<MetadataAttribute> meta) {
        this.meta = meta;
    }


}
