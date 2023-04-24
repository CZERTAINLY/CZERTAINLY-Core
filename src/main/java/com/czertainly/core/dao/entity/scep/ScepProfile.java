package com.czertainly.core.dao.entity.scep;

import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.core.scep.ScepProfileDetailDto;
import com.czertainly.api.model.core.scep.ScepProfileDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.UniquelyIdentifiedAndAudited;
import com.czertainly.core.service.model.Securable;
import com.czertainly.core.service.scep.impl.ScepServiceImpl;
import com.czertainly.core.util.*;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "scep_profile")
public class ScepProfile extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<ScepProfileDto>, ObjectAccessControlMapper<NameAndUuidDto>, Securable {

    @Column(name="name")
    private String name;

    @Column(name="description")
    private String description;

    @Column(name="enabled")
    private Boolean isEnabled;

    @Column(name = "require_manual_approval")
    private Boolean requireManualApproval;

    @OneToOne
    @JsonBackReference
    @JoinColumn(name = "ra_profile_uuid", insertable = false, updatable = false)
    private RaProfile raProfile;

    @Column(name = "ra_profile_uuid")
    private UUID raProfileUuid;

    @Column(name = "issue_certificate_attributes")
    private String issueCertificateAttributes;

    @Column(name = "revoke_certificate_attributes")
    private String revokeCertificateAttributes;

    @OneToOne
    @JsonBackReference
    @JoinColumn(name = "ca_certificate_uuid", insertable = false, updatable = false)
    private Certificate caCertificate;

    @Column(name = "ca_certificate_uuid")
    private UUID caCertificateUuid;

    @Column(name = "renew_threshold")
    private Integer renewalThreshold;

    @Column(name = "include_ca_certificate")
    private boolean includeCaCertificate = false;

    @Column(name = "include_ca_certificate_chain")
    private boolean includeCaCertificateChain = false;

    @Column(name = "challenge_password")
    private String challengePassword;

    @Column(name = "intune_tenant")
    private String intuneTenant;

    @Column(name = "intune_application_id")
    private String intuneApplicationId;

    @Column(name = "intune_application_key")
    private String intuneApplicationKey;

    @Override
    public ScepProfileDto mapToDto() {
        ScepProfileDto scepProfileDto = new ScepProfileDto();
        if(raProfile != null) {
            scepProfileDto.setRaProfileName(raProfile.getName());
            scepProfileDto.setRaProfileUuid(raProfile.getUuid().toString());
        }
        scepProfileDto.setDescription(description);
        scepProfileDto.setEnabled(isEnabled);
        scepProfileDto.setName(name);
        scepProfileDto.setUuid(uuid.toString());
        scepProfileDto.setRequireManualApproval(requireManualApproval);
        scepProfileDto.setIncludeCaCertificate(includeCaCertificate);
        scepProfileDto.setIncludeCaCertificateChain(includeCaCertificateChain);
        scepProfileDto.setRenewThreshold(renewalThreshold);
        return scepProfileDto;
    }

    public ScepProfileDetailDto mapToDetailDto() {
        ScepProfileDetailDto scepProfileDto = new ScepProfileDetailDto();
        if(raProfile != null) {
            scepProfileDto.setRaProfile(raProfile.mapToDto());
        }
        scepProfileDto.setDescription(description);
        scepProfileDto.setEnabled(isEnabled);
        scepProfileDto.setName(name);
        scepProfileDto.setUuid(uuid.toString());
        scepProfileDto.setRequireManualApproval(requireManualApproval);
        scepProfileDto.setIssueCertificateAttributes(AttributeDefinitionUtils.getResponseAttributes(AttributeDefinitionUtils.deserialize(issueCertificateAttributes, DataAttribute.class)));
        scepProfileDto.setIncludeCaCertificate(includeCaCertificate);
        scepProfileDto.setIncludeCaCertificateChain(includeCaCertificateChain);
        scepProfileDto.setRenewThreshold(renewalThreshold);
        if(caCertificate != null) scepProfileDto.setCaCertificate(caCertificate.mapToListDto());
        if(raProfile != null) {
            scepProfileDto.setScepUrl(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                    + ScepServiceImpl.SCEP_URL_PREFIX + "/" + name + "/pkiclient.exe");
        }
        scepProfileDto.setIntuneTenant(intuneTenant);
        scepProfileDto.setIntuneApplicationId(intuneApplicationId);
        // Custom Attributes for the DTO should be set in the methods which require the detail DTO
        return scepProfileDto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("description", description)
                .append("name", name)
                .append("isEnabled", isEnabled)
                .append("requireManualApproval", requireManualApproval)
                .append("issueCertificateAttributes", issueCertificateAttributes)
                .append("revokeCertificateAttributes", revokeCertificateAttributes)
                .toString();
    }

    @Override
    public NameAndUuidDto mapToAccessControlObjects() {
        return new NameAndUuidDto(uuid.toString(), name);
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

    public Boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(Boolean enabled) {
        isEnabled = enabled;
    }

    public Boolean getRequireManualApproval() {
        return requireManualApproval;
    }

    public void setRequireManualApproval(Boolean requireManualApproval) {
        this.requireManualApproval = requireManualApproval;
    }

    public RaProfile getRaProfile() {
        return raProfile;
    }

    public void setRaProfile(RaProfile raProfile) {
        this.raProfile = raProfile;
        if(raProfile != null) this.raProfileUuid = raProfile.getUuid();
        else this.raProfileUuid = null;
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

    public UUID getRaProfileUuid() {
        return raProfileUuid;
    }

    public void setRaProfileUuid(UUID raProfileUuid) {
        this.raProfileUuid = raProfileUuid;
    }

    public Certificate getCaCertificate() {
        return caCertificate;
    }

    public void setCaCertificate(Certificate caCertificate) {
        this.caCertificate = caCertificate;
        if(caCertificate != null) this.setCaCertificateUuid( caCertificate.getUuid() );
    }

    public UUID getCaCertificateUuid() {
        return caCertificateUuid;
    }

    public void setCaCertificateUuid(UUID caCertificateUuid) {
        this.caCertificateUuid = caCertificateUuid;
    }

    public Integer getRenewalThreshold() {
        return renewalThreshold;
    }

    public void setRenewalThreshold(Integer renewThreshold) {
        this.renewalThreshold = renewThreshold;
    }

    public boolean isIncludeCaCertificate() {
        return includeCaCertificate;
    }

    public void setIncludeCaCertificate(boolean includeCaCertificate) {
        this.includeCaCertificate = includeCaCertificate;
    }

    public boolean isIncludeCaCertificateChain() {
        return includeCaCertificateChain;
    }

    public void setIncludeCaCertificateChain(boolean includeCaCertificateChain) {
        this.includeCaCertificateChain = includeCaCertificateChain;
    }

    public String getChallengePassword() {
        if (challengePassword != null) {
            return SecretsUtil.decodeAndDecryptSecretString(challengePassword, SecretEncodingVersion.V1);
        }
        return null;
    }

    public void setChallengePassword(String challengePassword) {
        if (challengePassword != null) {
            this.challengePassword = SecretsUtil.encryptAndEncodeSecretString(challengePassword, SecretEncodingVersion.V1);
        } else {
            this.challengePassword = null;
        }
    }

    public String getIntuneTenant() {
        return intuneTenant;
    }

    public void setIntuneTenant(String intuneTenant) {
        this.intuneTenant = intuneTenant;
    }

    public String getIntuneApplicationId() {
        return intuneApplicationId;
    }

    public void setIntuneApplicationId(String intuneApplicationId) {
        this.intuneApplicationId = intuneApplicationId;
    }

    public String getIntuneApplicationKey() {
        return intuneApplicationKey;
    }

    public void setIntuneApplicationKey(String intuneApplicationKey) {
        this.intuneApplicationKey = intuneApplicationKey;
    }
}
