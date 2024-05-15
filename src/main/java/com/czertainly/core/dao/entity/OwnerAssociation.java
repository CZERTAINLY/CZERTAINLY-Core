package com.czertainly.core.dao.entity;

import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "owner_association")
@DiscriminatorValue("OWNER")
public class OwnerAssociation extends ResourceObjectAssociation {
    @Column(name = "owner_uuid")
    private UUID ownerUuid;

    @Column(name = "owner_username")
    private String ownerUsername;

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
