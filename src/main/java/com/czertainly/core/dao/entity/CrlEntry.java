package com.czertainly.core.dao.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "crl_entry")
public class CrlEntry extends UniquelyIdentified {

    @EmbeddedId
    private CrlEntryId id = new CrlEntryId();
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("crl_uuid")
    private UUID crlUuid;

    @MapsId("serialNumber")
    private String serialNumber;

    @Column(name = "revocation_date")
    private Date revocationDate;

    @Column(name = "revocation_reason")
    private String revocationReason;

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public CrlEntryId getId() {
        return id;
    }

    public void setId(CrlEntryId id) {
        this.id = id;
    }

    public UUID getCrlUuid() {
        return crlUuid;
    }

    public void setCrlUuid(UUID crlUuid) {
        this.crlUuid = crlUuid;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass())
            return false;

        CrlEntry that = (CrlEntry) o;
        return Objects.equals(crlUuid, that.crlUuid) &&
                Objects.equals(serialNumber, that.serialNumber);
    }

}
