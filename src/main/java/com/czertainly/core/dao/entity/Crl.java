package com.czertainly.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Setter
@Entity
@Table(name = "crl")
public class Crl extends UniquelyIdentified {
    @Column(name = "ca_certificate_uuid")
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

    @OneToMany(mappedBy = "crl", fetch = FetchType.LAZY)
    @JsonBackReference
    private List<CrlEntry> crlEntries;

    public Map<String, CrlEntry> getCrlEntriesMap() {
        return crlEntries.stream().collect(Collectors.toMap(crlEntry -> crlEntry.getId().getSerialNumber(), crlEntry -> crlEntry));
    }
}
