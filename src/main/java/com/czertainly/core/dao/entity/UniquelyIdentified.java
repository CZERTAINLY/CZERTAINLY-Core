package com.czertainly.core.dao.entity;

import com.czertainly.core.security.authz.SecuredUUID;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

import java.util.UUID;

@MappedSuperclass
public abstract class UniquelyIdentified {

    @Id
    @Column(name = "uuid", nullable = false, updatable = false)
    public UUID uuid;

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = UUID.fromString(uuid);
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public SecuredUUID getSecuredUuid() {
        return SecuredUUID.fromUUID(uuid);
    }

    @PrePersist
    private void generateUuid() {
        if (uuid == null) {
            setUuid(UUID.randomUUID().toString());
        }
    }

}
