package com.czertainly.core.dao.entity;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.czertainly.core.util.DtoMapper;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.czertainly.api.model.discovery.CertificateEntityCode;
import com.czertainly.api.model.discovery.CertificateEntityDto;

@Entity
@Table(name = "certificate_entity")
public class CertificateEntity extends Audited implements Serializable, DtoMapper<CertificateEntityDto> {

    /**
     *
     */
    private static final long serialVersionUID = 6407781756692461875L;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "certificate_entity_seq")
    @SequenceGenerator(name = "certificate_entity_seq", sequenceName = "certificate_entity_id_seq", allocationSize = 1)
    protected Long id;

    @Column(name = "name")
    protected String name;

    @Column(name = "description")
    protected String description;

    @Column(name = "entity_type")
    @Enumerated(EnumType.STRING)
    protected CertificateEntityCode entityType;

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

    public CertificateEntityCode getEntityType() {
        return entityType;
    }

    public void setEntityType(CertificateEntityCode entityType) {
        this.entityType = entityType;
    }

    @Override
    public CertificateEntityDto mapToDto() {
        CertificateEntityDto dto = new CertificateEntityDto();
        dto.setId(this.id);
        dto.setName(this.name);
        dto.setUuid(uuid);
        dto.setDescription(description);
        dto.setEntityType(entityType);
        return dto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("id", id)
                .append("name", name)
                .append("description", description)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CertificateEntity that = (CertificateEntity) o;
        return new EqualsBuilder().append(id, that.id).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(id).toHashCode();
    }
}
