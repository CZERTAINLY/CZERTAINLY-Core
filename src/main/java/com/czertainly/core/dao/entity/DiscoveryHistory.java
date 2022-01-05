package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.discovery.DiscoveryHistoryDto;
import com.czertainly.api.model.core.discovery.DiscoveryStatus;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.MetaDefinitions;
import com.fasterxml.jackson.annotation.JsonBackReference;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "discovery_history")
public class DiscoveryHistory extends Audited implements Serializable, DtoMapper<DiscoveryHistoryDto> {

    /**
     *
     */
    private static final long serialVersionUID = 571684590427678474L;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "discovery_seq")
    @SequenceGenerator(name = "discovery_seq", sequenceName = "discovery_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "kind")
    private String kind;

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

    @Column(name = "meta")
    private String meta;

    @Column(name = "connector_uuid")
    private String connectorUuid;
    
    @Column(name = "connector_name")
    private String connectorName;

    @Column(name = "attributes")
    private String attributes;

    @JsonBackReference
    @OneToMany(mappedBy = "discovery")
    private Set<DiscoveryCertificate> certificate = new HashSet<>();

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).append("id", id).append("uuid", uuid)
                .append("totalCertificatesDiscovered", totalCertificatesDiscovered).toString();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getConnectorUuid() {
        return connectorUuid;
    }

    public void setConnectorUuid(String connectorUuid) {
        this.connectorUuid = connectorUuid;
    }

    public String getAttributes() {
        return attributes;
    }

    public void setAttributes(String attributes) {
        this.attributes = attributes;
    }

    public String getMeta() {
        return meta;
    }

    public void setMeta(String meta) {
        this.meta = meta;
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

	@Override
    public DiscoveryHistoryDto mapToDto() {
        DiscoveryHistoryDto dto = new DiscoveryHistoryDto();
        dto.setUuid(uuid);
        dto.setName(name);
        dto.setEndTime(endTime);
        dto.setStartTime(startTime);
        dto.setMeta(MetaDefinitions.deserialize(meta));
        dto.setTotalCertificatesDiscovered(totalCertificatesDiscovered);
        dto.setStatus(status);
        dto.setCertificate(certificate.stream().map(DiscoveryCertificate::mapToDto).collect(Collectors.toList()));
        dto.setConnectorUuid(connectorUuid);
        dto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(AttributeDefinitionUtils.deserialize(attributes)));
        dto.setKind(kind);
        dto.setMessage(message);
        dto.setConnectorName(connectorName);
        return dto;
    }

}
