package com.czertainly.core.dao.entity.acme;

import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.core.acme.AcmeProfileDto;
import com.czertainly.api.model.core.acme.AcmeProfileListDto;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.UniquelyIdentifiedAndAudited;
import com.czertainly.core.service.acme.impl.ExtendedAcmeHelperService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.ObjectAccessControlMapper;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "acme_profile")
public class AcmeProfile extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<AcmeProfileDto>, ObjectAccessControlMapper<NameAndUuidDto> {

    @Column(name="name")
    private String name;

    @Column(name="description")
    private String description;

    @Column(name="is_enabled")
    private Boolean isEnabled;

    @Column(name="terms_of_service_url")
    private String termsOfServiceUrl;

    @Column(name="dns_resolver_ip")
    private String dnsResolverIp;

    @Column(name="dns_resolver_port")
    private String dnsResolverPort;

    @OneToOne(fetch = FetchType.LAZY)
    @JsonBackReference
    @JoinColumn(name = "ra_profile_uuid", insertable = false, updatable = false)
    private RaProfile raProfile;

    @Column(name = "ra_profile_uuid")
    private UUID raProfileUuid;

    @Column(name = "issue_certificate_attributes")
    private String issueCertificateAttributes;

    @Column(name = "revoke_certificate_attributes")
    private String revokeCertificateAttributes;

    @Column(name = "website_url")
    private String website;

    @Column(name="validity")
    private Integer validity;

    @Column(name="retry_interval")
    private Integer retryInterval;

    @Column(name= "disable_new_orders")
    private Boolean disableNewOrders;

    @Column(name = "terms_of_service_change_url")
    private String termsOfServiceChangeUrl;

    @Column(name= "require_contact")
    private Boolean requireContact;

    @Column(name= "require_terms_of_service")
    private Boolean requireTermsOfService;

    @Override
    public AcmeProfileDto mapToDto() {
        AcmeProfileDto acmeProfileDto = new AcmeProfileDto();
        if(raProfile != null) {
            acmeProfileDto.setRaProfile(raProfile.mapToDto());
        }
        acmeProfileDto.setDescription(description);
        acmeProfileDto.setEnabled(isEnabled);
        acmeProfileDto.setName(name);
        acmeProfileDto.setUuid(uuid.toString());
        acmeProfileDto.setDnsResolverIp(dnsResolverIp);
        acmeProfileDto.setDnsResolverPort(dnsResolverPort);
        acmeProfileDto.setRetryInterval(retryInterval);
        acmeProfileDto.setTermsOfServiceChangeDisable(disableNewOrders);
        acmeProfileDto.setTermsOfServiceUrl(termsOfServiceUrl);
        acmeProfileDto.setValidity(validity);
        acmeProfileDto.setRequireContact(requireContact);
        acmeProfileDto.setRequireTermsOfService(requireTermsOfService);
        acmeProfileDto.setWebsiteUrl(website);
        acmeProfileDto.setTermsOfServiceChangeUrl(termsOfServiceChangeUrl);
        if(raProfile != null){
            acmeProfileDto.setDirectoryUrl(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + ExtendedAcmeHelperService.ACME_URI_HEADER + "/" + name + "/directory");
            }
        acmeProfileDto.setRevokeCertificateAttributes(AttributeDefinitionUtils.getResponseAttributes(AttributeDefinitionUtils.deserialize(revokeCertificateAttributes, DataAttribute.class)));
        acmeProfileDto.setIssueCertificateAttributes(AttributeDefinitionUtils.getResponseAttributes(AttributeDefinitionUtils.deserialize(issueCertificateAttributes, DataAttribute.class)));
        return acmeProfileDto;
    }

    public AcmeProfileListDto mapToDtoSimple() {
        AcmeProfileListDto acmeProfileDto = new AcmeProfileListDto();
        if(raProfile != null) {
            acmeProfileDto.setRaProfileName(raProfile.getName());
            acmeProfileDto.setRaProfileUuid(raProfile.getUuid().toString());
        }
        acmeProfileDto.setDescription(description);
        acmeProfileDto.setEnabled(isEnabled);
        acmeProfileDto.setName(name);
        acmeProfileDto.setUuid(uuid.toString());
        if(raProfile != null) {
            acmeProfileDto.setDirectoryUrl(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                    + ExtendedAcmeHelperService.ACME_URI_HEADER + "/" + name + "/directory");
        }
        return acmeProfileDto;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("description", description)
                .append("name", name)
                .append("isEnabled", isEnabled)
                .append("termsOfServiceUrl", termsOfServiceUrl)
                .append("dnsResolverIp", dnsResolverIp)
                .append("dnsResolverPort", dnsResolverPort)
                .append("termsOfServiceChangeUrl", termsOfServiceChangeUrl)
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

    public String getTermsOfServiceUrl() {
        return termsOfServiceUrl;
    }

    public void setTermsOfServiceUrl(String termsOfServiceUrl) {
        this.termsOfServiceUrl = termsOfServiceUrl;
    }

    public String getDnsResolverIp() {
        return dnsResolverIp;
    }

    public void setDnsResolverIp(String dnsResolverIp) {
        this.dnsResolverIp = dnsResolverIp;
    }

    public String getDnsResolverPort() {
        return dnsResolverPort;
    }

    public void setDnsResolverPort(String dnsResolverPort) {
        this.dnsResolverPort = dnsResolverPort;
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

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public Integer getValidity() {
        return validity;
    }

    public void setValidity(Integer validity) {
        this.validity = validity;
    }

    public Integer getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(Integer retryInterval) {
        this.retryInterval = retryInterval;
    }

    public Boolean isDisableNewOrders() {
        return disableNewOrders;
    }

    public void setDisableNewOrders(Boolean disableNewOrders) {
        this.disableNewOrders = disableNewOrders;
    }

    public Boolean isRequireContact() {
        return requireContact;
    }

    public void setRequireContact(Boolean insistContact) {
        this.requireContact = insistContact;
    }

    public Boolean isRequireTermsOfService() {
        return requireTermsOfService;
    }

    public void setRequireTermsOfService(Boolean isRequireTermsOfService) {
        this.requireTermsOfService = isRequireTermsOfService;
    }

    public String getTermsOfServiceChangeUrl() {
        return termsOfServiceChangeUrl;
    }

    public void setTermsOfServiceChangeUrl(String termsOfServiceChangeUrl) {
        this.termsOfServiceChangeUrl = termsOfServiceChangeUrl;
    }

    public UUID getRaProfileUuid() {
        return raProfileUuid;
    }

    public void setRaProfileUuid(UUID raProfileUuid) {
        this.raProfileUuid = raProfileUuid;
    }

}
