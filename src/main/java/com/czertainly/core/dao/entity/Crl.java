package com.czertainly.core.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "crl")
public class Crl extends UniquelyIdentified {
    @Column(name = "ca_certificate_uuid", nullable = false)
    private UUID caCertificateUuid;

    @Column(name = "issuer_dn", nullable = false)
    private String issuerDn;

    @Column(name = "serial_number", nullable = false)
    private String serialNumber;

    @Column(name = "crl_issuer_dn", nullable = false)
    private String crlIssuerDn;

    @Column(name = "crl_number", nullable = false)
    private String crlNumber;

    @Column(name = "next_update", nullable = false)
    private Date nextUpdate;

    @Column(name = "crl_number_delta")
    private String crlNumberDelta;

    @Column(name = "next_update_delta")
    private Date nextUpdateDelta;

    @Column(name = "last_revocation_date")
    private Date lastRevocationDate;

    @OneToMany(mappedBy = "crl")
    private List<CrlEntry> crlEntries;


    public UUID getCaCertificateUuid() {
        return caCertificateUuid;
    }

    public void setCaCertificateUuid(UUID caCertificateUuuid) {
        this.caCertificateUuid = caCertificateUuuid;
    }

    public String getIssuerDn() {
        return issuerDn;
    }

    public void setIssuerDn(String issuerDn) {
        this.issuerDn = issuerDn;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getCrlIssuerDn() {
        return crlIssuerDn;
    }

    public void setCrlIssuerDn(String crlIssuerDn) {
        this.crlIssuerDn = crlIssuerDn;
    }

    public String getCrlNumber() {
        return crlNumber;
    }

    public void setCrlNumber(String crlNumber) {
        this.crlNumber = crlNumber;
    }

    public Date getNextUpdate() {
        return nextUpdate;
    }

    public void setNextUpdate(Date nextUpdate) {
        this.nextUpdate = nextUpdate;
    }

    public String getCrlNumberDelta() {
        return crlNumberDelta;
    }

    public void setCrlNumberDelta(String crlNumberDelta) {
        this.crlNumberDelta = crlNumberDelta;
    }

    public Date getNextUpdateDelta() {
        return nextUpdateDelta;
    }

    public void setNextUpdateDelta(Date nextUpdateDelta) {
        this.nextUpdateDelta = nextUpdateDelta;
    }

    public Date getLastRevocationDate() {
        return lastRevocationDate;
    }

    public void setLastRevocationDate(Date lastRevocationDate) {
        this.lastRevocationDate = lastRevocationDate;
    }

    public List<CrlEntry> getCrlEntries() {
        return crlEntries;
    }

    public void setCrlEntries(List<CrlEntry> crlEntries) {
        this.crlEntries = crlEntries;
    }
}
