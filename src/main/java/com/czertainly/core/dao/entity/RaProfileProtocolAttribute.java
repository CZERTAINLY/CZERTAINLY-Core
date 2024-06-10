package com.czertainly.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Entity
@Table(name = "ra_profile_protocol_attribute")
public class RaProfileProtocolAttribute extends UniquelyIdentified {

    @OneToOne(fetch = FetchType.LAZY)
    @JsonBackReference
    @JoinColumn(name = "ra_profile_uuid", insertable = false, updatable = false)
    private RaProfile raProfile;

    @Setter
    @Column(name = "ra_profile_uuid")
    private UUID raProfileUuid;

    /**
     * ACME related attributes
     */

    @Setter
    @Column(name = "acme_issue_certificate_attributes")
    private String acmeIssueCertificateAttributes;

    @Setter
    @Column(name = "acme_revoke_certificate_attributes")
    private String acmeRevokeCertificateAttributes;

    /**
     * SCEP related attributes
     */

    @Setter
    @Column(name = "scep_issue_certificate_attributes")
    private String scepIssueCertificateAttributes;

    /**
     * CMP protocol related attributes
     */
    @Setter
    @Column(name = "cmp_issue_certificate_attributes")
    private String cmpIssueCertificateAttributes;

    @Setter
    @Column(name = "cmp_revoke_certificate_attributes")
    private String cmpRevokeCertificateAttributes;

    public void setRaProfile(RaProfile raProfile) {
        this.raProfile = raProfile;
        if(raProfile != null) raProfileUuid = raProfile.getUuid();
    }

}
