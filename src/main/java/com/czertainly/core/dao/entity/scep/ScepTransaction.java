package com.czertainly.core.dao.entity.scep;

import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.core.scep.ScepProfileDetailDto;
import com.czertainly.api.model.core.scep.ScepProfileDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.UniquelyIdentified;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "scep_transaction")
public class ScepTransaction extends UniquelyIdentified {

    @Column(name="transaction_id")
    private String transactionId;

    @OneToOne
    @JsonBackReference
    @JoinColumn(name = "certificate_uuid", insertable = false, updatable = false)
    private Certificate certificate;

    @Column(name = "certificate_uuid")
    private UUID certificateUuid;

    @OneToOne
    @JsonBackReference
    @JoinColumn(name = "scep_profile_uuid", insertable = false, updatable = false)
    private ScepProfile scepProfile;

    @Column(name = "scep_profile_uuid")
    private UUID scepProfileUuid;

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public Certificate getCertificate() {
        return certificate;
    }

    public void setCertificate(Certificate certificate) {
        this.certificate = certificate;
        if(certificate != null) certificateUuid = certificate.getUuid();
    }

    public UUID getCertificateUuid() {
        return certificateUuid;
    }

    public void setCertificateUuid(UUID certificateUuid) {
        this.certificateUuid = certificateUuid;
    }

    public ScepProfile getScepProfile() {
        return scepProfile;
    }

    public void setScepProfile(ScepProfile scepProfile) {
        this.scepProfile = scepProfile;
        if(scepProfile != null) this.scepProfileUuid = scepProfile.getUuid();
    }

    public UUID getScepProfileUuid() {
        return scepProfileUuid;
    }

    public void setScepProfileUuid(UUID scepProfileUuid) {
        this.scepProfileUuid = scepProfileUuid;
    }
}
