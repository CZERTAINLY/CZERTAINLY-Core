package com.czertainly.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

import java.util.UUID;


@Entity
@Table(name = "ra_profile_protocol_attribute")
public class RaProfileProtocolAttribute extends UniquelyIdentified {

    @OneToOne(fetch = FetchType.LAZY)
    @JsonBackReference
    @JoinColumn(name = "ra_profile_uuid", insertable = false, updatable = false)
    private RaProfile raProfile;

    @Column(name = "ra_profile_uuid")
    private UUID raProfileUuid;

    /**
     * ACME related attributes
     */

    @Column(name = "acme_issue_certificate_attributes")
    private String acmeIssueCertificateAttributes;

    @Column(name = "acme_revoke_certificate_attributes")
    private String acmeRevokeCertificateAttributes;

    /**
     * SCEP related attributes
     */

    @Column(name = "scep_issue_certificate_attributes")
    private String scepIssueCertificateAttributes;


    public String getAcmeIssueCertificateAttributes() {
        return acmeIssueCertificateAttributes;
    }

    public void setAcmeIssueCertificateAttributes(String acmeIssueCertificateAttributes) {
        this.acmeIssueCertificateAttributes = acmeIssueCertificateAttributes;
    }

    public String getAcmeRevokeCertificateAttributes() {
        return acmeRevokeCertificateAttributes;
    }

    public void setAcmeRevokeCertificateAttributes(String acmeRevokeCertificateAttributes) {
        this.acmeRevokeCertificateAttributes = acmeRevokeCertificateAttributes;
    }

    public String getScepIssueCertificateAttributes() {
        return scepIssueCertificateAttributes;
    }

    public void setScepIssueCertificateAttributes(String scepIssueCertificateAttributes) {
        this.scepIssueCertificateAttributes = scepIssueCertificateAttributes;
    }

    public RaProfile getRaProfile() {
        return raProfile;
    }

    public void setRaProfile(RaProfile raProfile) {
        this.raProfile = raProfile;
        if(raProfile != null) raProfileUuid = raProfile.getUuid();
    }

    public UUID getRaProfileUuid() {
        return raProfileUuid;
    }

    public void setRaProfileUuid(UUID raProfileUuid) {
        this.raProfileUuid = raProfileUuid;
    }
}
