package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.auth.Resource;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.hibernate.annotations.DiscriminatorOptions;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.NaturalId;

import java.util.UUID;

@Getter
@Setter
@Entity
//@Table(name = "resource_object_association")
//@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorValue("OWNER")
public class OwnerAssociation extends ResourceObjectAssociation {
    @Column(name = "owner_uuid")
    private UUID ownerUuid;

    @Column(name = "owner_username")
    private String ownerUsername;

    public NameAndUuidDto getOwnerInfo() {
        if(ownerInfo == null) {
            ownerInfo = new NameAndUuidDto(ownerUuid.toString(), ownerUsername);
        }
        return ownerInfo;
    }

    @Transient
    private NameAndUuidDto ownerInfo;

//    @JsonBackReference
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "group_uuid", insertable = false, updatable = false)
//    private Group group;

//    @Any
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
                .append("resource", getResource())
                .append("objectUuid", getObjectUuid())
                .append("ownerUuid", ownerUuid)
                .append("ownerUsername", ownerUsername)
                .toString();
    }
}
