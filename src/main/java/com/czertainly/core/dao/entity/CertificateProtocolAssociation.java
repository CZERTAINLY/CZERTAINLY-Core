package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.enums.CertificateProtocol;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "certificate_protocol_association")
public class CertificateProtocolAssociation extends UniquelyIdentified implements Serializable {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_uuid", insertable = false, updatable = false)
    private Certificate certificate;

    @Column(name = "certificate_uuid", nullable = false)
    private UUID certificateUuid;

    @Column(name = "protocol", nullable = false)
    @Enumerated(EnumType.STRING)
    private CertificateProtocol protocol;

    @Column(name = "protocol_profile_uuid", nullable = false)
    private UUID protocolProfileUuid;

    @Column(name = "additional_protocol_uuid")
    private UUID additionalProtocolUuid;


    public void setCertificate(Certificate certificate) {
        this.certificate = certificate;
        if(certificate != null) certificateUuid = certificate.getUuid();
    }




}
