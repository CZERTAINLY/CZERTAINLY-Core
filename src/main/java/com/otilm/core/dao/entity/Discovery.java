package com.otilm.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.otilm.api.model.client.discovery.DiscoveryDetailDto;
import com.otilm.api.model.client.discovery.DiscoveryListDto;
import com.otilm.api.model.connector.discovery.v2.DiscoveryProgressDto;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLJoinTableRestriction;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "discovery")
public class Discovery extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<DiscoveryDetailDto> {

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
    private OffsetDateTime startTime;

    @Column(name = "end_time")
    private OffsetDateTime endTime;

    @Column(name = "total_certificates_discovered")
    private Integer totalCertificatesDiscovered;

    @Column(name = "connector_total_certificates_discovered")
    private Integer connectorTotalCertificatesDiscovered;

    @Column(name = "connector_uuid")
    private UUID connectorUuid;

    @Column(name = "connector_name")
    private String connectorName;

    // ---- Discovery v2 run columns. All null (or zero, for the cursor) on a v1 legacy run. ----

    // NULL = v1 legacy run; set = the connector interface this run was initiated against.
    @Column(name = "connector_interface_uuid")
    private UUID connectorInterfaceUuid;

    // Connector-side run context the lifecycle calls replay; nulled on every terminal transition.
    // S1948: every entity is Serializable via UniquelyIdentifiedObject, but nothing Java-serializes them —
    // Jackson owns this JSONB field's persistence shape (same situation as Certificate's attribute lists).
    @SuppressWarnings("java:S1948")
    @Column(name = "run_meta", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> runMeta;

    // TEXT[] of enum member names, the platform's shape for a flat enum list (connector_interface.features).
    @Enumerated(EnumType.STRING)
    @Column(name = "resources", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<Resource> resources;

    // Run-wide highest item sequence applied to staging — the drain cursor. Item sequences start at 1, so 0
    // means nothing drained yet.
    @Column(name = "last_applied_sequence", nullable = false)
    private long lastAppliedSequence;

    // The connector's latest progress report, written and read as one snapshot: a single value makes a torn
    // snapshot — fields mixed from two reports — unrepresentable under the concurrent writers (status poll,
    // progress event). S1948: see runMeta.
    @SuppressWarnings("java:S1948")
    @Column(name = "progress", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private DiscoveryProgressDto progress;

    @Column(name = "run_messages", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> runMessages;

    @Column(name = "stopped_at")
    private OffsetDateTime stoppedAt;

    // Last authoritative DiscoveryRunState wire code the connector reported; "completed" switches the drain
    // into drain-to-completion mode.
    @Column(name = "connector_state")
    private String connectorState;

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
        // The contract publishes both fields as always present. Every run this Core can hold ran against a
        // v1 discovery connector, so the v1 synthesis is exact: certificates only, never stoppable.
        dto.setResources(List.of(Resource.CERTIFICATE));
        dto.setStoppable(false);
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        Class<?> oEffectiveClass = o instanceof HibernateProxy hibernateProxy
                ? hibernateProxy.getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy hibernateProxy
                ? hibernateProxy.getHibernateLazyInitializer().getPersistentClass()
                : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) {
            return false;
        }
        Discovery that = (Discovery) o;
        return getUuid() != null && Objects.equals(getUuid(), that.getUuid());
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy hibernateProxy
                ? hibernateProxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }
}
