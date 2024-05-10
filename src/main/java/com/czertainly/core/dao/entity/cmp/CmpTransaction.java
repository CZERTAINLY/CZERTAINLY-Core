package com.czertainly.core.dao.entity.cmp;

import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.UniquelyIdentified;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "cmp_transaction")
public class CmpTransaction extends UniquelyIdentified {

    @Column(name="transaction_id")
    private String transactionId;
    public String getTransactionId() {
        return transactionId;
    }
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    @Column(name = "certificate_uuid")
    private UUID certificateUuid;
    public UUID getCertificateUuid() {
        return certificateUuid;
    }
    public void setCertificateUuid(UUID certificateUuid) {
        this.certificateUuid = certificateUuid;
    }

    @Column(name = "cmp_profile_uuid")
    private UUID cmpProfileUuid;
    public UUID getCmpProfileUuid() {
        return cmpProfileUuid;
    }
    public void setCmpProfileUuid(UUID cmpProfileUuid) {
        this.cmpProfileUuid = cmpProfileUuid;
    }

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
