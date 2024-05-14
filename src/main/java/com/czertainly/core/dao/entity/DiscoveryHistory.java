package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.discovery.DiscoveryHistoryDetailDto;
import com.czertainly.api.model.client.discovery.DiscoveryHistoryDto;
import com.czertainly.api.model.core.discovery.DiscoveryStatus;
import com.czertainly.core.util.DtoMapper;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.hibernate.annotations.WhereJoinTable;

import java.io.Serializable;
import java.util.*;

@Entity
@Table(name = "discovery_history")
public class DiscoveryHistory extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<DiscoveryHistoryDetailDto> {

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

    @JsonBackReference
    @OneToMany(mappedBy = "discovery", fetch = FetchType.LAZY)
    private Set<DiscoveryCertificate> certificate = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "rule_trigger_2_object",
            joinColumns = @JoinColumn(name = "object_uuid", referencedColumnName = "uuid", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT), insertable = false, updatable = false),
            inverseJoinColumns = @JoinColumn(name = "trigger_uuid", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT), insertable = false, updatable = false)
    )
    @WhereJoinTable(clause = "resource = 'DISCOVERY'")
    private List<RuleTrigger> triggers = new ArrayList<>();

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
    public DiscoveryHistoryDetailDto mapToDto() {
        DiscoveryHistoryDetailDto dto = new DiscoveryHistoryDetailDto();
        dto.setUuid(uuid.toString());
        dto.setName(name);
        dto.setEndTime(endTime);
        dto.setStartTime(startTime);
        dto.setTotalCertificatesDiscovered(totalCertificatesDiscovered);
        dto.setStatus(status);
        dto.setConnectorUuid(connectorUuid.toString());
        dto.setKind(kind);
        dto.setMessage(message);
        dto.setConnectorName(connectorName);
        dto.setTriggers(triggers.stream().map(RuleTrigger::mapToDto).toList());
        return dto;
    }

    public DiscoveryHistoryDto mapToListDto() {
        DiscoveryHistoryDto dto = new DiscoveryHistoryDto();
        dto.setUuid(uuid.toString());
        dto.setName(name);
        dto.setEndTime(endTime);
        dto.setStartTime(startTime);
        dto.setTotalCertificatesDiscovered(totalCertificatesDiscovered);
        dto.setStatus(status);
        dto.setConnectorUuid(connectorUuid.toString());
        dto.setKind(kind);
        dto.setConnectorName(connectorName);
        return dto;
    }

}
