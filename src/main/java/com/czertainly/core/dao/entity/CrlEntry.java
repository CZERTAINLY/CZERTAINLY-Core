package com.czertainly.core.dao.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "crl_entry")
public class CrlEntry implements Serializable {

    @EmbeddedId
    private CrlEntryId id = new CrlEntryId();

    @ManyToOne
    @MapsId("crlUuid")
    private Crl crl;

//    @MapsId("serialNumber")
//    private String serialNumber;

    @Column(name = "revocation_date")
    private Date revocationDate;

    @Column(name = "revocation_reason")
    private String revocationReason;

    public CrlEntry() {};

//    public String getSerialNumber() {
//        return serialNumber;
//    }
//
//    public void setSerialNumber(String serialNumber) {
//        this.serialNumber = serialNumber;
//    }

    public CrlEntryId getId() {
        return id;
    }

    public void setId(CrlEntryId id) {
        this.id = id;
    }

    public UUID getCrlUuid() {
        return crl.getUuid();
    }

    public Date getRevocationDate() {
        return revocationDate;
    }

    public void setRevocationDate(Date revocationDate) {
        this.revocationDate = revocationDate;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public void setRevocationReason(String revocationReason) {
        this.revocationReason = revocationReason;
    }

    public Crl getCrl() {
        return crl;
    }

    public void setCrl(Crl crl) {
        this.crl = crl;
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
                ", revocationReason='" + revocationReason+ '\'' +
                ", revocationDate='" + revocationDate + '\'' +
                '}';
    }



}
