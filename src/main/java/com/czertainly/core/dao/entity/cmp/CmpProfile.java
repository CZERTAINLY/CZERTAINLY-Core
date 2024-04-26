package com.czertainly.core.dao.entity.cmp;

import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.cmp.CmpProfileDetailDto;
import com.czertainly.api.model.core.cmp.CmpProfileDto;
import com.czertainly.api.model.core.cmp.ProtectionMethod;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.UniquelyIdentifiedAndAudited;
import com.czertainly.core.service.cmp.CmpConstants;
import com.czertainly.core.service.model.Securable;
import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.ObjectAccessControlMapper;
import com.czertainly.core.util.SecretEncodingVersion;
import com.czertainly.core.util.SecretsUtil;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "cmp_profile")
public class CmpProfile extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<CmpProfileDto>, ObjectAccessControlMapper<NameAndUuidDto>, Securable {

    @Setter
    @Getter
    @Column(name="name")
    private String name;

    @Setter
    @Getter
    @Column(name="description")
    private String description;

    @Setter
    @Getter
    @Column(name="enabled")
    private Boolean enabled;
    public Boolean isEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    @Getter
    @OneToOne(fetch = FetchType.LAZY)
    @JsonBackReference
    @JoinColumn(name = "ra_profile_uuid", insertable = false, updatable = false)
    private RaProfile raProfile;

    @Setter
    @Getter
    @Column(name = "ra_profile_uuid")
    private UUID raProfileUuid;

    @Setter
    @Getter
    @Column(name = "request_protection_method")
    @Enumerated(EnumType.STRING)
    private ProtectionMethod requestProtectionMethod;

    @Setter
    @Getter
    @Column(name = "response_protection_method")
    @Enumerated(EnumType.STRING)
    private ProtectionMethod responseProtectionMethod;

    @Column(name = "shared_secret")
    private String sharedSecret;

    @Getter
    @OneToOne(fetch = FetchType.LAZY)
    @JsonBackReference
    @JoinColumn(name = "signing_certificate_uuid", insertable = false, updatable = false)
    private Certificate signingCertificate;

    @Setter
    @Getter
    @Column(name = "signing_certificate_uuid")
    private UUID signingCertificateUuid;

    @Override
    public CmpProfileDto mapToDto() {
        CmpProfileDto cmpProfileDto = new CmpProfileDto();
        if(raProfile != null) {
            cmpProfileDto.setRaProfile(raProfile.mapToDtoSimplified());
            cmpProfileDto.setCmpUrl(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                    + CmpConstants.CMP_BASE_CONTEXT + "/" + name);
        }
        cmpProfileDto.setDescription(description);
        cmpProfileDto.setEnabled(enabled);
        cmpProfileDto.setName(name);
        cmpProfileDto.setUuid(uuid.toString());
        return cmpProfileDto;
    }

    public CmpProfileDetailDto mapToDetailDto() {
        CmpProfileDetailDto cmpProfileDto = new CmpProfileDetailDto();
        if(raProfile != null) {
            cmpProfileDto.setRaProfile(raProfile.mapToDtoSimplified());
        }
        cmpProfileDto.setDescription(description);
        cmpProfileDto.setEnabled(enabled);
        cmpProfileDto.setName(name);
        cmpProfileDto.setUuid(uuid.toString());
        if(signingCertificate != null) cmpProfileDto.setSigningCertificate(signingCertificate.mapToListDto());
        if(raProfile != null) {
            cmpProfileDto.setCmpUrl(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                    + CmpConstants.CMP_BASE_CONTEXT + "/" + name);
        }
        // Custom Attributes for the DTO should be set in the methods which require the detail DTO
        return cmpProfileDto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("description", description)
                .append("name", name)
                .append("uuid", uuid)
                .append("enabled", enabled)
                .toString();
    }

    @Override
    public NameAndUuidDto mapToAccessControlObjects() {
        return new NameAndUuidDto(uuid.toString(), name);
    }

    public void setRaProfile(RaProfile raProfile) {
        this.raProfile = raProfile;
        if(raProfile != null) this.raProfileUuid = raProfile.getUuid();
        else this.raProfileUuid = null;
    }

    public void setSigningCertificate(Certificate signingCertificate) {
        this.signingCertificate = signingCertificate;
        if(signingCertificate != null) this.setSigningCertificateUuid( signingCertificate.getUuid() );
    }

    public String getSharedSecret() {
        if (sharedSecret != null) {
            return SecretsUtil.decodeAndDecryptSecretString(sharedSecret, SecretEncodingVersion.V1);
        }
        return null;
    }

    public void setSharedSecret(String sharedSecret) {
        if (sharedSecret != null) {
            this.sharedSecret = SecretsUtil.encryptAndEncodeSecretString(sharedSecret, SecretEncodingVersion.V1);
        } else {
            this.sharedSecret = null;
        }
    }

}
