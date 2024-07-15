package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.entity.EntityInstanceDto;
import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.ObjectAccessControlMapper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "entity_instance_reference")
public class EntityInstanceReference extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<EntityInstanceDto>, ObjectAccessControlMapper<NameAndUuidDto> {

    @Column(name = "entity_instance_uuid")
    private String entityInstanceUuid;

    @Column(name = "name")
    private String name;

    @Column(name = "status")
    private String status;

    @Column(name = "kind")
    private String kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_uuid", insertable = false, updatable = false)
    @ToString.Exclude
    private Connector connector;

    @Column(name = "connector_uuid")
    private UUID connectorUuid;

    @Column(name="connector_name")
    private String connectorName;

    @OneToMany(mappedBy = "entityInstanceReference", fetch = FetchType.LAZY)
    @JsonIgnore
    @ToString.Exclude
    private Set<Location> locations = new HashSet<>();

    public void setConnector(Connector connector) {
        this.connector = connector;
        if(connector != null) this.connectorUuid = connector.getUuid();
        else this.connectorUuid = null;
    }

    @Override
    public EntityInstanceDto mapToDto() {
        EntityInstanceDto dto = new EntityInstanceDto();
        dto.setUuid(this.uuid.toString());
        dto.setName(this.name);
        dto.setStatus(this.status);
        dto.setKind(kind);
        dto.setConnectorName(this.connectorName);
        if (this.connectorUuid != null) {
            dto.setConnectorUuid(this.connectorUuid.toString());
        }
        return dto;
    }

    @Override
    public NameAndUuidDto mapToAccessControlObjects() {
        return new NameAndUuidDto(uuid.toString(), name);
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        EntityInstanceReference that = (EntityInstanceReference) o;
        return getUuid() != null && Objects.equals(getUuid(), that.getUuid());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
