package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.raprofile.RaProfileAcmeDetailResponse;
import com.czertainly.api.model.core.raprofile.RaProfileDto;
import com.czertainly.core.dao.entity.acme.AcmeProfile;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.DtoMapper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


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

    @Column(name = "authority_instance_name")
    private String authorityInstanceName;

    //    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "attributes", length = 4096)
    private String attributes;

    @ManyToOne
    @JoinColumn(name = "authority_instance_ref_id")
    private AuthorityInstanceReference authorityInstanceReference;

    @Column(name = "enabled")
    private Boolean enabled;

    @ManyToMany
    @JoinTable(
            name = "client_authorization",
            joinColumns = @JoinColumn(name = "ra_profile_id"),
            inverseJoinColumns = @JoinColumn(name = "client_id"))
    @JsonIgnore
    private Set<Client> clients = new HashSet<>();

    /**
     * Acme related objects for RA Profile
     */
    @OneToOne
    @JoinColumn(name="acme_profile_id")
    private AcmeProfile acmeProfile;

    @Column(name="acme_issue_certificate_attributes")
    private String issueCertificateAttributes;

    @Column(name="acme_revoke_certificate_attributes")
    private String revokeCertificateAttributes;

    public RaProfileAcmeDetailResponse mapToAcmeDto(){
        RaProfileAcmeDetailResponse dto = new RaProfileAcmeDetailResponse();
        if(acmeProfile == null){
            dto.setAcmeAvailable(false);
            return dto;
        }
        dto.setName(acmeProfile.getName());
        dto.setUuid(acmeProfile.getUuid());
        dto.setIssueCertificateAttributes(AttributeDefinitionUtils.getResponseAttributes(AttributeDefinitionUtils.deserialize(issueCertificateAttributes)));
        dto.setRevokeCertificateAttributes(AttributeDefinitionUtils.getResponseAttributes(AttributeDefinitionUtils.deserialize(revokeCertificateAttributes)));
        dto.setDirectoryUrl(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + "/acme/raProfile/" + name + "/directory");
        dto.setAcmeAvailable(true);
        return dto;
    }

    public RaProfileDto mapToDtoSimple() {
        RaProfileDto dto = new RaProfileDto();
        dto.setUuid(this.uuid);
        dto.setName(this.name);
        if(acmeProfile != null) {
            dto.setEnabledProtocols(List.of("ACME"));
        }
        dto.setDescription(this.description);
        dto.setAuthorityInstanceName(this.authorityInstanceName);
        try {
            dto.setAuthorityInstanceUuid(this.authorityInstanceReference.getUuid());
        }catch (NullPointerException e){
            dto.setAuthorityInstanceUuid(null);
        }
        dto.setEnabled(this.enabled);

        return dto;
    }

    @Override
    @Transient
    public RaProfileDto mapToDto() {
        RaProfileDto dto = new RaProfileDto();
        dto.setUuid(uuid);
        dto.setName(name);
        dto.setDescription(this.description);
        dto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(AttributeDefinitionUtils.deserialize(this.attributes)));
        dto.setAuthorityInstanceUuid(authorityInstanceReference != null ? authorityInstanceReference.getUuid() : null);
        dto.setAuthorityInstanceName(this.authorityInstanceName);
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

    public String getAuthorityInstanceName() {
        return authorityInstanceName;
    }

    public void setAuthorityInstanceName(String authorityInstanceName) {
        this.authorityInstanceName = authorityInstanceName;
    }

    public AuthorityInstanceReference getAuthorityInstanceReference() {
        return authorityInstanceReference;
    }

    public void setAuthorityInstanceReference(AuthorityInstanceReference authorityInstanceReference) {
        this.authorityInstanceReference = authorityInstanceReference;
    }

    public AcmeProfile getAcmeProfile() {
        return acmeProfile;
    }

    public void setAcmeProfile(AcmeProfile acmeProfile) {
        this.acmeProfile = acmeProfile;
    }

    public String getIssueCertificateAttributes() {
        return issueCertificateAttributes;
    }

    public void setIssueCertificateAttributes(String issueCertificateAttributes) {
        this.issueCertificateAttributes = issueCertificateAttributes;
    }

    public String getRevokeCertificateAttributes() {
        return revokeCertificateAttributes;
    }

    public void setRevokeCertificateAttributes(String revokeCertificateAttributes) {
        this.revokeCertificateAttributes = revokeCertificateAttributes;
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
