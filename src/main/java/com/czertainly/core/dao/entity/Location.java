package com.czertainly.core.dao.entity;


import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.core.location.CertificateInLocationDto;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.SecretMaskingUtil;
import com.fasterxml.jackson.annotation.JsonBackReference;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "location")
public class Location extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<LocationDto> {

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
    @JoinColumn(name = "entity_instance_ref_uuid", insertable = false, updatable = false)
    private EntityInstanceReference entityInstanceReference;

    @Column(name = "entity_instance_ref_uuid")
    private UUID entityInstanceReferenceUuid;

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

    public void setAttributes(List<DataAttribute> attributes) {
        this.attributes = AttributeDefinitionUtils.serialize(attributes);
    }

    public EntityInstanceReference getEntityInstanceReference() {
        return entityInstanceReference;
    }

    public void setEntityInstanceReference(EntityInstanceReference entityInstanceReference) {
        this.entityInstanceReference = entityInstanceReference;
        if(entityInstanceReference != null) this.entityInstanceReferenceUuid = entityInstanceReference.getUuid();
        else this.entityInstanceReferenceUuid = null;
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

    public UUID getEntityInstanceReferenceUuid() {
        return entityInstanceReferenceUuid;
    }

    public void setEntityInstanceReferenceUuid(UUID entityInstanceReferenceUuid) {
        this.entityInstanceReferenceUuid = entityInstanceReferenceUuid;
    }

    public void setEntityInstanceReferenceUuid(String entityInstanceReferenceUuid) {
        this.entityInstanceReferenceUuid = UUID.fromString(entityInstanceReferenceUuid);
    }

    @Override
    @Transient
    public LocationDto mapToDto() {
        LocationDto dto = new LocationDto();
        dto.setUuid(uuid.toString());
        dto.setName(name);
        dto.setDescription(this.description);
        dto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(AttributeDefinitionUtils.deserialize(this.attributes, DataAttribute.class)));
        dto.setEntityInstanceUuid(entityInstanceReference != null ? entityInstanceReference.getUuid().toString() : null);
        dto.setEntityInstanceName(this.entityInstanceName);
        dto.setEnabled(enabled);
        dto.setSupportMultipleEntries(supportMultipleEntries);
        dto.setSupportKeyManagement(supportKeyManagement);
        List<CertificateInLocationDto> cilDtoList = new ArrayList<>();

        List<CertificateLocation> orderedList = certificates.stream().sorted(Comparator.comparing(CertificateLocation::getCreated).reversed()).collect(Collectors.toList());
        for (CertificateLocation certificateLocation : orderedList) {
            CertificateInLocationDto cilDto = new CertificateInLocationDto();
            cilDto.setCommonName(certificateLocation.getCertificate().getCommonName());
            cilDto.setSerialNumber(certificateLocation.getCertificate().getSerialNumber());
            cilDto.setCertificateUuid(certificateLocation.getCertificate().getUuid().toString());
            cilDto.setWithKey(certificateLocation.isWithKey());
            cilDto.setPushAttributes(SecretMaskingUtil.maskSecret(AttributeDefinitionUtils.getResponseAttributes(certificateLocation.getPushAttributes())));
            cilDto.setCsrAttributes(SecretMaskingUtil.maskSecret(AttributeDefinitionUtils.getResponseAttributes(certificateLocation.getCsrAttributes())));

            cilDtoList.add(cilDto);
        }
        dto.setCertificates(cilDtoList);

        return dto;
    }

    public LocationDto mapToDtoSimple() {
        LocationDto dto = new LocationDto();
        dto.setUuid(uuid.toString());
        dto.setName(name);
        dto.setDescription(this.description);
        dto.setEntityInstanceUuid(entityInstanceReference != null ? entityInstanceReference.getUuid().toString() : null);
        dto.setEntityInstanceName(this.entityInstanceName);
        dto.setEnabled(enabled);
        dto.setSupportMultipleEntries(supportMultipleEntries);
        dto.setSupportKeyManagement(supportKeyManagement);

        return dto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("uuid", uuid)
                .append("name", name)
                .append("enabled", enabled)
                .toString();
    }
}
