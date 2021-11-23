package com.czertainly.core.dao.entity;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.czertainly.core.util.DtoMapper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.czertainly.api.model.raprofile.RaProfileDto;
import com.czertainly.core.util.AttributeDefinitionUtils;


@Entity
@Table(name = "ra_profile")
public class RaProfile extends Audited implements Serializable, DtoMapper<RaProfileDto> {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ra_profile_seq")
    @SequenceGenerator(name = "ra_profile_seq", sequenceName = "ra_profile_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "ca_instance_name")
    private String caInstanceName;

    //    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "attributes", length = 4096)
    private String attributes;

    @ManyToOne
    @JoinColumn(name = "ca_instance_ref_id")
    private CAInstanceReference caInstanceReference;

    @Column(name = "enabled")
    private Boolean enabled;

    @ManyToMany
    @JoinTable(
            name = "client_authorization",
            joinColumns = @JoinColumn(name = "ra_profile_id"),
            inverseJoinColumns = @JoinColumn(name = "client_id"))
    @JsonIgnore
    private Set<Client> clients = new HashSet<>();

    public RaProfileDto mapToDtoSimple() {
        RaProfileDto dto = new RaProfileDto();
        dto.setId(this.id);
        dto.setUuid(this.uuid);
        dto.setName(this.name);
        dto.setDescription(this.description);
        dto.setCaInstanceName(this.caInstanceName);
        try {
            dto.setCaInstanceUuid(this.caInstanceReference.getUuid());
        }catch (NullPointerException e){
            dto.setCaInstanceUuid(null);
        }
        dto.setEnabled(this.enabled);

        return dto;
    }

    @Override
    @Transient
    public RaProfileDto mapToDto() {
        RaProfileDto dto = new RaProfileDto();
        dto.setId(id);
        dto.setUuid(uuid);
        dto.setName(name);
        dto.setDescription(this.description);
        dto.setAttributes(AttributeDefinitionUtils.deserialize(this.attributes));
        dto.setCaInstanceUuid(caInstanceReference != null ? caInstanceReference.getUuid() : null);
        dto.setCaInstanceName(this.caInstanceName);
        dto.setEnabled(enabled);
        return dto;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAttributes() {
        return attributes;
    }

    public void setAttributes(String attributes) {
        this.attributes = attributes;
    }

    public CAInstanceReference getCaInstanceReference() {
        return caInstanceReference;
    }

    public void setCaInstanceReference(CAInstanceReference caInstanceReference) {
        this.caInstanceReference = caInstanceReference;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Set<Client> getClients() {
        return clients;
    }

    public void setClients(Set<Client> clients) {
        this.clients = clients;
    }

    public String getCaInstanceName() {
        return caInstanceName;
    }

    public void setCaInstanceName(String caInstanceName) {
        this.caInstanceName = caInstanceName;
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
