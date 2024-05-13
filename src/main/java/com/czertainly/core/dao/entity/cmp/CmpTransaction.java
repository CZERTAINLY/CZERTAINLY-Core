package com.czertainly.core.dao.entity.cmp;

import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.UniquelyIdentified;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "cmp_transaction")
public class CmpTransaction extends UniquelyIdentified {

    @Setter
    @Getter
    @Column(name="transaction_id")
    private String transactionId;

    @Setter
    @Getter
    @Column(name = "certificate_uuid")
    private UUID certificateUuid;

    @Setter
    @Getter
    @Column(name = "cmp_profile_uuid")
    private UUID cmpProfileUuid;

    public enum CmpTransactionState {CERT_ISSUED, CERT_REKEYED, CERT_CONFIRMED, CERT_REVOKED, FAILED;}
    @Setter
    @Getter
    @Column(name = "state")
    @Enumerated(EnumType.STRING)
    private CmpTransactionState state;

    @Setter
    @Getter
    @Column(name = "custom_reason")
    private String customReason;

    @OneToOne(fetch = FetchType.LAZY)
    @JsonBackReference
    @JoinColumn(name = "cmp_profile_uuid", insertable = false, updatable = false)
    private CmpProfile cmpProfile;
    public CmpProfile getCmpProfile() {
        return cmpProfile;
    }
    public void setCmpProfile(CmpProfile cmpProfile) {
        this.cmpProfile = cmpProfile;
        if(cmpProfile != null) this.cmpProfileUuid = cmpProfile.getUuid();
    }

    @OneToOne(fetch = FetchType.LAZY)
    @JsonBackReference
    @JoinColumn(name = "certificate_uuid", insertable = false, updatable = false)
    private Certificate certificate;
    public Certificate getCertificate() {
        return certificate;
    }
    public void setCertificate(Certificate certificate) {
        this.certificate = certificate;
        if(certificate != null) certificateUuid = certificate.getUuid();
    }
}
