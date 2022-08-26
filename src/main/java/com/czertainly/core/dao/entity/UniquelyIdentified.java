package com.czertainly.core.dao.entity;

import com.czertainly.core.security.authz.SecuredUUID;
import org.hibernate.annotations.Type;
import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import java.util.UUID;

@MappedSuperclass
public abstract class UniquelyIdentified {

    @Id
    @Column(name = "uuid", nullable = false, updatable = false)
    protected String uuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public SecuredUUID getSecuredUuid() {
        return SecuredUUID.fromString(uuid);
    }

    @PrePersist
    private void generateUuid(){
        if (uuid == null) {
            setUuid(UUID.randomUUID().toString());
        }
    }

}
