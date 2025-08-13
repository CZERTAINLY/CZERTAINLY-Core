package com.czertainly.core.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Setter
@Getter
@ToString
@RequiredArgsConstructor
@AllArgsConstructor
@Embeddable
public class CertificateRelationId implements Serializable {

    @Column(name = "certificate_uuid")
    private UUID certificateUuid;

    @Column(name = "source_certificate_uuid")
    private UUID sourceCertificateUuid;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CertificateRelationId that)) return false;
        return Objects.equals(certificateUuid, that.getCertificateUuid()) &&
                Objects.equals(sourceCertificateUuid, that.sourceCertificateUuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(certificateUuid, sourceCertificateUuid);
    }
}
