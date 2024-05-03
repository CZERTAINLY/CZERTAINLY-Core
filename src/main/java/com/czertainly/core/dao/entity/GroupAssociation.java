package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.auth.Resource;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
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
@DiscriminatorValue("GROUP")
public class GroupAssociation extends ResourceObjectAssociation {
    @Column(name = "group_uuid")
    private UUID groupUuid;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_uuid", insertable = false, updatable = false)
    private Group group;

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
                .append("groupUuid", groupUuid)
                .toString();
    }
}
