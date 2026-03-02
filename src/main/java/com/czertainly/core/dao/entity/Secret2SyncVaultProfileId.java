package com.czertainly.core.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
@ToString
@RequiredArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Secret2SyncVaultProfileId implements Serializable {

    @Column(name = "secret_uuid" )
    private UUID secretUuid;

    @Column(name = "sync_vault_profile_uuid" )
    private UUID syncVaultProfileUuid;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Secret2SyncVaultProfileId other)) return false;
        return Objects.equals(secretUuid, other.secretUuid) && Objects.equals(syncVaultProfileUuid, other.syncVaultProfileUuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(secretUuid, syncVaultProfileUuid);
    }

}