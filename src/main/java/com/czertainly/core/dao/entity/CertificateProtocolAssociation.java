package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.enums.CertificateProtocol;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

import java.io.Serializable;
import java.util.Objects;
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

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        CertificateProtocolAssociation that = (CertificateProtocolAssociation) o;
        return getUuid() != null && Objects.equals(getUuid(), that.getUuid());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }

}
