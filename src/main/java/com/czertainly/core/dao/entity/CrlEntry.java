package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.authority.CertificateRevocationReason;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "crl_entry")
public class CrlEntry implements Serializable {

    @EmbeddedId
    private CrlEntryId id = new CrlEntryId();

    @ManyToOne
    @MapsId("crlUuid")
    private Crl crl;

    @Column(name = "revocation_date", nullable = false)
    private Date revocationDate;

    @Column(name = "revocation_reason", nullable = false)
    @Enumerated(EnumType.STRING)
    private CertificateRevocationReason revocationReason;

    public UUID getCrlUuid() {
        return crl.getUuid();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass())
            return false;

        CrlEntry that = (CrlEntry) o;
        return Objects.equals(crl, that.crl) &&
                Objects.equals(id.getSerialNumber(), that.getId().getSerialNumber());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getCrlUuid(), id.getSerialNumber());
    }

    @Override
    public String toString() {
        return "CertificateLocation{" +
                "id=" + id +
                ", revocationReason='" + revocationReason + '\'' +
                ", revocationDate='" + revocationDate + '\'' +
                '}';
    }


}
