package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.api.model.core.raprofile.RaProfileDto;
import com.czertainly.core.dao.entity.acme.AcmeProfile;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.DtoMapper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "location")
public class Location extends Audited implements Serializable, DtoMapper<LocationDto> {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "location_seq")
    @SequenceGenerator(name = "location_seq", sequenceName = "location_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "entity_instance_name")
    private String entityInstanceName;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "attributes", length = 4096)
    private String attributes;

    @ManyToOne
    @JoinColumn(name = "entity_instance_ref_id")
    private EntityInstanceReference entityInstanceReference;

    @Column(name = "enabled")
    private Boolean enabled;

    @OneToMany(
            mappedBy = "certificate",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private Set<CertificateLocation> certificates = new HashSet<>();

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEntityInstanceName() {
        return entityInstanceName;
    }

    public void setEntityInstanceName(String entityInstanceName) {
        this.entityInstanceName = entityInstanceName;
    }

    public String getAttributes() {
        return attributes;
    }

    public void setAttributes(String attributes) {
        this.attributes = attributes;
    }

    public EntityInstanceReference getEntityInstanceReference() {
        return entityInstanceReference;
    }

    public void setEntityInstanceReference(EntityInstanceReference entityInstanceReference) {
        this.entityInstanceReference = entityInstanceReference;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Set<CertificateLocation> getCertificates() {
        return certificates;
    }

    public void setCertificates(Set<CertificateLocation> certificates) {
        this.certificates = certificates;
    }

    @Override
    @Transient
    public LocationDto mapToDto() {
        LocationDto dto = new LocationDto();
        dto.setUuid(uuid);
        dto.setName(name);
        dto.setDescription(this.description);
        dto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(AttributeDefinitionUtils.deserialize(this.attributes)));
        dto.setEntityInstanceUuid(entityInstanceReference != null ? entityInstanceReference.getUuid() : null);
        dto.setEntityInstanceName(this.entityInstanceName);
        dto.setEnabled(enabled);
        return dto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("id", id)
                .append("uuid", uuid)
                .append("name", name)
                .append("enabled", enabled)
                .toString();
    }
}
