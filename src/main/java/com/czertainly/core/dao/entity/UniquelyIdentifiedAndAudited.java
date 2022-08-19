package com.czertainly.core.dao.entity;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import java.util.UUID;

@MappedSuperclass
public abstract class UniquelyIdentifiedAndAudited extends Audited {

    @Id
    @Column(name = "uuid", nullable = false, updatable = false)
    protected String uuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @PrePersist
    private void generateUuid(){
        if (uuid == null) {
            setUuid(UUID.randomUUID().toString());
        }
    }

}
