package com.czertainly.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "group_association")
public class GroupAssociation extends ResourceObjectAssociation {
    @Column(name = "group_uuid", nullable = false)
    private UUID groupUuid;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_uuid", nullable = false, insertable = false, updatable = false)
    private Group group;

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
