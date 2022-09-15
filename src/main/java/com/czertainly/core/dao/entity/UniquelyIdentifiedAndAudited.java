package com.czertainly.core.dao.entity;

import com.czertainly.core.security.authz.SecuredUUID;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import java.util.UUID;

@MappedSuperclass
public abstract class UniquelyIdentifiedAndAudited extends Audited {

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
            setUuid(UUID.randomUUID());
        }
    }

}
