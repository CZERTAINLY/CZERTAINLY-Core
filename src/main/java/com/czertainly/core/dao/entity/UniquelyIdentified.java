package com.czertainly.core.dao.entity;

import com.czertainly.core.security.authz.SecuredUUID;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import java.util.UUID;

@MappedSuperclass
public abstract class UniquelyIdentified {

    @Column(name = "uuid")
    protected String uuid = UUID.randomUUID().toString();

    public String getUuid() {
        return uuid;
    }

    public SecuredUUID getSecuredUuid() {
        return SecuredUUID.fromString(uuid);
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}
