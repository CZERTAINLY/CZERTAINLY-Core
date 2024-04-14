package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.raprofile.RaProfileAcmeDetailResponseDto;
import com.czertainly.api.model.client.raprofile.RaProfileCmpDetailResponseDto;
import com.czertainly.api.model.client.raprofile.RaProfileScepDetailResponseDto;
import com.czertainly.api.model.client.raprofile.SimplifiedRaProfileDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.raprofile.RaProfileDto;
import com.czertainly.core.dao.entity.acme.AcmeProfile;
import com.czertainly.core.dao.entity.cmp.CmpProfile;
import com.czertainly.core.dao.entity.scep.ScepProfile;
import com.czertainly.core.service.acme.AcmeConstants;
import com.czertainly.core.service.cmp.CmpConstants;
import com.czertainly.core.service.model.Securable;
import com.czertainly.core.service.scep.impl.ScepServiceImpl;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.ObjectAccessControlMapper;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "ra_profile")
public class RaProfile extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<RaProfileDto>, Securable, ObjectAccessControlMapper<NameAndUuidDto> {

    @Setter
    @Getter
    @Column(name = "name")
    private String name;

    @Setter
    @Getter
    @Column(name = "description")
    private String description;

    @Setter
    @Getter
    @Column(name = "authority_instance_name")
    private String authorityInstanceName;

    @Getter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "authority_instance_ref_uuid", insertable = false, updatable = false)
    private AuthorityInstanceReference authorityInstanceReference;

    @Getter
    @Column(name = "authority_instance_ref_uuid")
    private UUID authorityInstanceReferenceUuid;

    @Setter
    @Getter
    @Column(name = "enabled")
    private Boolean enabled;

    @Setter
    @Getter
    @Column(name = "authority_certificate_uuid")
    private UUID authorityCertificateUuid;

    @Setter
    @Getter
    @ManyToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinTable(
            name = "ra_profile_2_compliance_profile",
            joinColumns = @JoinColumn(name = "ra_profile_uuid"),
            inverseJoinColumns = @JoinColumn(name = "compliance_profile_uuid"))
    private Set<ComplianceProfile> complianceProfiles;

    @Setter
    @OneToOne(mappedBy = "raProfile", fetch = FetchType.LAZY)
    private RaProfileProtocolAttribute protocolAttribute;

    /**
     * Acme related objects for RA Profile
     */
    @Getter
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acme_profile_uuid", insertable = false, updatable = false)
    private AcmeProfile acmeProfile;

    @Column(name = "acme_profile_uuid")
    private UUID acmeProfileUuid;

    @Getter
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scep_profile_uuid", insertable = false, updatable = false)
    private ScepProfile scepProfile;

    @Column(name = "scep_profile_uuid")
    private UUID scepProfileUuid;

    /**
     * CMP protocol related objects for RA Profile
     */
    @Getter
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cmp_profile_uuid", insertable = false, updatable = false)
    private CmpProfile cmpProfile;

    @Column(name = "cmp_profile_uuid")
    private UUID cmpProfileUuid;

    public RaProfileAcmeDetailResponseDto mapToAcmeDto() {
        RaProfileAcmeDetailResponseDto dto = new RaProfileAcmeDetailResponseDto();
        if (acmeProfile == null) {
            dto.setAcmeAvailable(false);
            return dto;
        }
        dto.setName(acmeProfile.getName());
        dto.setUuid(acmeProfile.getUuid().toString());
        dto.setIssueCertificateAttributes(AttributeDefinitionUtils.getResponseAttributes(AttributeDefinitionUtils.deserialize(protocolAttribute.getAcmeIssueCertificateAttributes(), DataAttribute.class)));
        dto.setRevokeCertificateAttributes(AttributeDefinitionUtils.getResponseAttributes(AttributeDefinitionUtils.deserialize(protocolAttribute.getAcmeRevokeCertificateAttributes(), DataAttribute.class)));
        dto.setDirectoryUrl(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + AcmeConstants.ACME_URI_HEADER + "/raProfile/" + name + "/directory");
        dto.setAcmeAvailable(true);
        return dto;
    }

    public RaProfileScepDetailResponseDto mapToScepDto() {
        RaProfileScepDetailResponseDto dto = new RaProfileScepDetailResponseDto();
        if (scepProfile == null) {
            dto.setScepAvailable(false);
            return dto;
        }
        dto.setScepAvailable(true);
        dto.setUrl(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                + ScepServiceImpl.SCEP_URL_PREFIX + "/" + name + "/pkiclient.exe");
        dto.setName(scepProfile.getName());
        dto.setUuid(scepProfile.getUuid().toString());
        dto.setIssueCertificateAttributes(AttributeDefinitionUtils.getResponseAttributes(AttributeDefinitionUtils.deserialize(protocolAttribute.getScepIssueCertificateAttributes(), DataAttribute.class)));
        return dto;
    }

    public RaProfileCmpDetailResponseDto mapToCmpDto() {
        RaProfileCmpDetailResponseDto dto = new RaProfileCmpDetailResponseDto();
        if (cmpProfile == null) {
            dto.setCmpAvailable(false);
            return dto;
        }
        dto.setName(cmpProfile.getName());
        dto.setUuid(cmpProfile.getUuid().toString());
        dto.setIssueCertificateAttributes(
                AttributeDefinitionUtils.getResponseAttributes(
                        AttributeDefinitionUtils.deserialize(
                                protocolAttribute.getCmpIssueCertificateAttributes(),
                                DataAttribute.class)
                )
        );
        dto.setRevokeCertificateAttributes(
                AttributeDefinitionUtils.getResponseAttributes(
                        AttributeDefinitionUtils.deserialize(
                                protocolAttribute.getCmpRevokeCertificateAttributes(),
                                DataAttribute.class)
                )
        );
        dto.setCmpUrl(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                + CmpConstants.CMP_BASE_CONTEXT + "/raProfile/" + name);
        dto.setCmpAvailable(true);
        return dto;
    }

    public RaProfileDto mapToDtoSimple() {
        RaProfileDto dto = new RaProfileDto();
        dto.setUuid(this.uuid.toString());
        dto.setName(this.name);
        List<String> enabledProtocols = new ArrayList<>();
        if (acmeProfile != null) {
            enabledProtocols.add("ACME");
        }
        if (scepProfile != null) {
            enabledProtocols.add("SCEP");
        }
        if (cmpProfile != null) {
            enabledProtocols.add("CMP");
        }
        dto.setEnabledProtocols(enabledProtocols);
        dto.setDescription(this.description);
        dto.setAuthorityInstanceName(this.authorityInstanceName);
        try {
            dto.setAuthorityInstanceUuid(this.authorityInstanceReference.getUuid().toString());
        } catch (NullPointerException e) {
            dto.setAuthorityInstanceUuid(null);
        }
        dto.setEnabled(this.enabled);

        return dto;
    }

    public SimplifiedRaProfileDto mapToDtoSimplified() {
        SimplifiedRaProfileDto dto = new SimplifiedRaProfileDto();
        dto.setUuid(this.uuid.toString());
        dto.setName(this.name);
        dto.setEnabled(this.enabled);
        try {
            dto.setAuthorityInstanceUuid(this.authorityInstanceReference.getUuid().toString());
        } catch (NullPointerException e) {
            dto.setAuthorityInstanceUuid(null);
        }
        return dto;
    }

    @Override
    @Transient
    public RaProfileDto mapToDto() {
        RaProfileDto dto = new RaProfileDto();
        dto.setUuid(uuid.toString());
        dto.setName(name);
        dto.setDescription(this.description);
        dto.setEnabled(enabled);

        if (authorityInstanceReference != null) {
            dto.setAuthorityInstanceUuid(authorityInstanceReference.getUuid().toString());
            dto.setAuthorityInstanceName(this.authorityInstanceName);
            dto.setLegacyAuthority(authorityInstanceReference.getConnector() == null ? null
                    : authorityInstanceReference.getConnector().getFunctionGroups().stream().anyMatch(fg -> fg.getFunctionGroup().getCode().equals(FunctionGroupCode.LEGACY_AUTHORITY_PROVIDER)));
        }
        return dto;
    }

    @Override
    public NameAndUuidDto mapToAccessControlObjects() {
        return new NameAndUuidDto(uuid.toString(), name);
    }

    public void setAuthorityInstanceReference(AuthorityInstanceReference authorityInstanceReference) {
        this.authorityInstanceReference = authorityInstanceReference;
        if (authorityInstanceReference != null)
            this.authorityInstanceReferenceUuid = authorityInstanceReference.getUuid();
        else this.authorityInstanceReferenceUuid = null;
    }

    public void setAuthorityInstanceReferenceUuid(UUID authorityInstanceReferenceUuid) {
        this.authorityInstanceReferenceUuid = authorityInstanceReferenceUuid;
    }

    public void setAuthorityInstanceReferenceUuid(String authorityInstanceReferenceUuid) {
        this.authorityInstanceReferenceUuid = UUID.fromString(authorityInstanceReferenceUuid);
    }

    public void setAcmeProfile(AcmeProfile acmeProfile) {
        this.acmeProfile = acmeProfile;
        if (acmeProfile != null) this.acmeProfileUuid = acmeProfile.getUuid();
        else this.acmeProfileUuid = null;
    }

    public void setScepProfile(ScepProfile scepProfile) {
        this.scepProfile = scepProfile;
        if (scepProfile != null) this.scepProfileUuid = scepProfile.getUuid();
        else this.scepProfileUuid = null;
    }

    public void setCmpProfile(CmpProfile cmpProfile) {
        this.cmpProfile = cmpProfile;
        if (cmpProfile != null) this.cmpProfileUuid = cmpProfile.getUuid();
        else this.cmpProfileUuid = null;
    }

    public RaProfileProtocolAttribute getProtocolAttribute() {
        if (protocolAttribute == null) {
            return new RaProfileProtocolAttribute();
        }
        return protocolAttribute;
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
