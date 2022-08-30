package com.czertainly.core.dao.entity;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import java.util.UUID;

@MappedSuperclass
public abstract class UniquelyIdentified {

    @Column(name = "uuid")
    protected String uuid;

    @PrePersist
    private void generateUuid(){
        if (uuid == null) {
            setUuid(UUID.randomUUID().toString());
        }
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}
