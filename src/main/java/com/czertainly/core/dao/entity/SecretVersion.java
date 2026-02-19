package com.czertainly.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "secret_version")
@Data
public class SecretVersion extends UniquelyIdentified {

    @Column(name = "secret_uuid", nullable = false)
    private UUID secretUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "secret_uuid", insertable = false, updatable = false)
    @JsonBackReference
    private Secret secret;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "fingerprint", nullable = false)
    private String fingerprint;

    @Column(name = "vaultInstanceUuid", nullable = false)
    private UUID vaultInstanceUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vaultInstanceUuid", insertable = false, updatable = false)
    @JsonBackReference
    private VaultInstance vaultInstance;

    @Column(name = "vault_version", nullable = false)
    private int vaultVersion;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

}
