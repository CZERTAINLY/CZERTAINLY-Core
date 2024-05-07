package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.auth.Resource;
import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.hibernate.annotations.*;

import java.io.Serializable;
import java.util.*;

@Getter
@Setter
@MappedSuperclass
public class ResourceObjectAssociation extends UniquelyIdentified {
    @Column(name = "resource", nullable = false)
    @Enumerated(EnumType.STRING)
    private Resource resource;

    @Column(name = "object_uuid", nullable = false)
    private UUID objectUuid;

//    @Any(optional = false, fetch = FetchType.LAZY)
//    @AnyDiscriminator(DiscriminatorType.STRING)
//    @AnyDiscriminatorValue(discriminator = "CERTIFICATE", entity = Certificate.class)
//    @AnyDiscriminatorValue(discriminator = "CRYPTOGRAPHIC_KEY", entity = CryptographicKey.class)
//    @AnyKeyJavaClass(UUID.class)
//    @Column(name = "resource")
//    @JoinColumn(name = "object_uuid")
//    private Serializable associatedObject;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("uuid", uuid)
                .append("resource", resource)
                .append("objectUuid", objectUuid)
                .toString();
    }
}

