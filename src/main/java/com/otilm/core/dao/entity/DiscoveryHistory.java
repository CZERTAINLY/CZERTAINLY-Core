package com.otilm.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.otilm.api.model.client.discovery.DiscoveryDetailDto;
import com.otilm.api.model.client.discovery.DiscoveryListDto;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.dao.entity.workflows.Trigger;
import com.otilm.core.util.DtoMapper;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLJoinTableRestriction;
import org.hibernate.proxy.HibernateProxy;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "discovery_history")
public class DiscoveryHistory extends UniquelyIdentifiedAndAudited
        implements
            Serializable,
            DtoMapper<DiscoveryDetailDto> {

    @Serial
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

    @Column(name = "connector_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private DiscoveryStatus connectorStatus;

    @Column(name = "message")
    private String message;

    @Column(name = "start_time")
    private Date startTime;

    @Column(name = "end_time")
    private Date endTime;

    @Column(name = "total_certificates_discovered")
    private Integer totalCertificatesDiscovered;

    @Column(name = "connector_total_certificates_discovered")
    private Integer connectorTotalCertificatesDiscovered;

    @Column(name = "connector_uuid")
    private UUID connectorUuid;

    @Column(name = "connector_name")
    private String connectorName;

    @JsonBackReference
    @OneToMany(mappedBy = "discovery", fetch = FetchType.LAZY)
    @ToString.Exclude
    private Set<DiscoveryCertificate> certificate = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "trigger_association",
            joinColumns = @JoinColumn(name = "object_uuid", referencedColumnName = "uuid", insertable = false,
                    updatable = false),
            inverseJoinColumns = @JoinColumn(name = "trigger_uuid", insertable = false, updatable = false),
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            inverseForeignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @SQLJoinTableRestriction("resource = 'DISCOVERY'")
    @ToString.Exclude
    private List<Trigger> triggers = new ArrayList<>();

    @Override
    public DiscoveryDetailDto mapToDto() {
        DiscoveryDetailDto dto = new DiscoveryDetailDto();
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
        dto.setTriggers(triggers.stream().map(Trigger::mapToDto).toList());
        dto.setConnectorStatus(connectorStatus);
        dto.setConnectorTotalCertificatesDiscovered(connectorTotalCertificatesDiscovered);
        // The contract publishes both lists as always present. Every run this Core can hold ran against a
        // v1 discovery connector, so the v1 synthesis is exact: certificates only, no lifecycle capabilities.
        // The discovery v2 implementation replaces these constants with the run's stored targets and the
        // capabilities synced from its connector.
        dto.setResources(List.of(Resource.CERTIFICATE));
        dto.setEffectiveCapabilities(List.of());
        return dto;
    }

    public DiscoveryListDto mapToListDto() {
        DiscoveryListDto dto = new DiscoveryListDto();
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

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        Class<?> oEffectiveClass = o instanceof HibernateProxy
                ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy
                ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass()
                : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) {
            return false;
        }
        DiscoveryHistory that = (DiscoveryHistory) o;
        return getUuid() != null && Objects.equals(getUuid(), that.getUuid());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy
                ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }
}
