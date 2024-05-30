package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.auth.Resource;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

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

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("uuid", uuid)
                .append("resource", resource)
                .append("objectUuid", objectUuid)
                .toString();
    }
}

