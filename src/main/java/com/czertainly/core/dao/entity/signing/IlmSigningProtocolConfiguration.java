package com.czertainly.core.dao.entity.signing;

import com.czertainly.api.model.client.attribute.ResponseAttribute;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolConfigurationDto;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolConfigurationListDto;
import com.czertainly.core.dao.entity.UniquelyIdentifiedAndAudited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "ilm_signing_protocol_configuration")
public class IlmSigningProtocolConfiguration extends UniquelyIdentifiedAndAudited  {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "default_signing_profile_uuid")
    private UUID defaultSigningProfileUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_signing_profile_uuid", insertable = false, updatable = false)
    @ToString.Exclude
    private SigningProfile defaultSigningProfile;

    public void setDefaultSigningProfile(SigningProfile defaultSigningProfile) {
        this.defaultSigningProfile = defaultSigningProfile;
        this.defaultSigningProfileUuid = defaultSigningProfile != null ? defaultSigningProfile.getUuid() : null;
    }

    public IlmSigningProtocolConfigurationDto mapToDto(List<ResponseAttribute> customAttributes) {
        IlmSigningProtocolConfigurationDto dto = new IlmSigningProtocolConfigurationDto();
        dto.setUuid(uuid.toString());
        dto.setName(name);
        dto.setDescription(description);
        dto.setCustomAttributes(customAttributes);
        return dto;
    }

    public IlmSigningProtocolConfigurationListDto mapToListDto() {
        IlmSigningProtocolConfigurationListDto dto = new IlmSigningProtocolConfigurationListDto();
        dto.setUuid(uuid.toString());
        dto.setName(name);
        return dto;
    }
}
