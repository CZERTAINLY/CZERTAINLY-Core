package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.AttributeDefinition;
import com.czertainly.api.model.common.RequestAttributeDto;
import com.czertainly.api.model.core.location.CertificateInLocationDto;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.MetaDefinitions;
import com.fasterxml.jackson.annotation.JsonBackReference;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;
import java.util.*;

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
            mappedBy = "location",
            cascade = CascadeType.ALL
            //orphanRemoval = true
    )
    @JsonBackReference
    private Set<CertificateLocation> certificates = new HashSet<>();

    @Column(name = "support_multi_entries")
    private boolean supportMultipleEntries;

    @Column(name = "support_key_mgmt")
    private boolean supportKeyManagement;

    @Column(name = "metadata")
    private String metadata;

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

    public List<RequestAttributeDto> getRequestAttributes() {
        return AttributeDefinitionUtils.deserializeRequestAttributes(this.attributes);
    }

    public void setAttributes(List<AttributeDefinition> attributes) {
        this.attributes = AttributeDefinitionUtils.serialize(attributes);
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

    public boolean isSupportMultipleEntries() {
        return supportMultipleEntries;
    }

    public void setSupportMultipleEntries(boolean supportMultipleEntries) {
        this.supportMultipleEntries = supportMultipleEntries;
    }

    public boolean isSupportKeyManagement() {
        return supportKeyManagement;
    }

    public void setSupportKeyManagement(boolean supportKeyManagement) {
        this.supportKeyManagement = supportKeyManagement;
    }

    public Map<String, Object> getMetadata() {
        return MetaDefinitions.deserialize(metadata);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = MetaDefinitions.serialize(metadata);
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
        dto.setSupportMultipleEntries(supportMultipleEntries);
        dto.setSupportKeyManagement(supportKeyManagement);
        dto.setMetadata(MetaDefinitions.deserialize(metadata));

        List<CertificateInLocationDto> cilDtoList = new ArrayList<>();
        for (CertificateLocation certificateLocation : this.certificates) {
            CertificateInLocationDto cilDto = new CertificateInLocationDto();
            cilDto.setMetadata(certificateLocation.getMetadata());
            cilDto.setCommonName(certificateLocation.getCertificate().getCommonName());
            cilDto.setSerialNumber(certificateLocation.getCertificate().getSerialNumber());
            cilDto.setCertificateUuid(certificateLocation.getCertificate().getUuid());
            cilDto.setWithKey(certificateLocation.isWithKey());

            cilDtoList.add(cilDto);
        }
        dto.setCertificates(cilDtoList);

        return dto;
    }

    public LocationDto mapToDtoSimple() {
        LocationDto dto = new LocationDto();
        dto.setUuid(uuid);
        dto.setName(name);
        dto.setDescription(this.description);
        dto.setEntityInstanceUuid(entityInstanceReference != null ? entityInstanceReference.getUuid() : null);
        dto.setEntityInstanceName(this.entityInstanceName);
        dto.setEnabled(enabled);
        dto.setSupportMultipleEntries(supportMultipleEntries);
        dto.setSupportKeyManagement(supportKeyManagement);
        dto.setMetadata(MetaDefinitions.deserialize(metadata));

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
