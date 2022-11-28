package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.core.discovery.DiscoveryHistoryDto;
import com.czertainly.api.model.core.discovery.DiscoveryStatus;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.SecretMaskingUtil;
import com.fasterxml.jackson.annotation.JsonBackReference;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "discovery_history")
public class DiscoveryHistory extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<DiscoveryHistoryDto> {

    /**
     *
     */
    private static final long serialVersionUID = 571684590427678474L;

    @Column(name = "name")
    private String name;

    @Column(name = "kind")
    private String kind;

    @Column(name = "discovery_connector_reference")
    private String discoveryConnectorReference;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private DiscoveryStatus status;

    @Column(name = "message")
    private String message;

    @Column(name = "start_time")
    private Date startTime;

    @Column(name = "end_time")
    private Date endTime;

    @Column(name = "total_certificates_discovered")
    private Integer totalCertificatesDiscovered;

    @Column(name = "connector_uuid")
    private UUID connectorUuid;
    
    @Column(name = "connector_name")
    private String connectorName;

    @Column(name = "attributes")
    private String attributes;

    @JsonBackReference
    @OneToMany(mappedBy = "discovery")
    private Set<DiscoveryCertificate> certificate = new HashSet<>();

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).append("uuid", uuid)
                .append("totalCertificatesDiscovered", totalCertificatesDiscovered).toString();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DiscoveryStatus getStatus() {
        return status;
    }

    public void setStatus(DiscoveryStatus status) {
        this.status = status;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public Integer getTotalCertificatesDiscovered() {
        return totalCertificatesDiscovered;
    }

    public void setTotalCertificatesDiscovered(Integer totalCertificatesDiscovered) {
        this.totalCertificatesDiscovered = totalCertificatesDiscovered;
    }


    public Set<DiscoveryCertificate> getCertificate() {
        return certificate;
    }

    public void setCertificate(Set<DiscoveryCertificate> certificate) {
        this.certificate = certificate;
    }

    public UUID getConnectorUuid() {
        return connectorUuid;
    }

    public void setConnectorUuid(UUID connectorUuid) {
        this.connectorUuid = connectorUuid;
    }

    public void setConnectorUuid(String connectorUuid) {
        this.connectorUuid = UUID.fromString(connectorUuid);
    }

    public String getAttributes() {
        return attributes;
    }

    public void setAttributes(String attributes) {
        this.attributes = attributes;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getConnectorName() {
		return connectorName;
	}

	public void setConnectorName(String connectorName) {
		this.connectorName = connectorName;
	}

    public String getDiscoveryConnectorReference() {
        return discoveryConnectorReference;
    }

    public void setDiscoveryConnectorReference(String discoveryConnectorReference) {
        this.discoveryConnectorReference = discoveryConnectorReference;
    }

    @Override
    public DiscoveryHistoryDto mapToDto() {
        DiscoveryHistoryDto dto = new DiscoveryHistoryDto();
        dto.setUuid(uuid.toString());
        dto.setName(name);
        dto.setEndTime(endTime);
        dto.setStartTime(startTime);
        dto.setTotalCertificatesDiscovered(totalCertificatesDiscovered);
        dto.setStatus(status);
        dto.setCertificate(certificate.stream().map(DiscoveryCertificate::mapToDto).collect(Collectors.toList()));
        dto.setConnectorUuid(connectorUuid.toString());
        List<DataAttribute> a = AttributeDefinitionUtils.deserialize(attributes, DataAttribute.class);
        dto.setAttributes(SecretMaskingUtil.maskSecret(AttributeDefinitionUtils.getResponseAttributes(a)));
        dto.setKind(kind);
        dto.setMessage(message);
        dto.setConnectorName(connectorName);
        return dto;
    }

}
