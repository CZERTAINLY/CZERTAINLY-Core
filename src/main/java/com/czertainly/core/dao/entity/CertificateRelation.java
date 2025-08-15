package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.certificate.CertificateRelationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "certificate_relation")
public class CertificateRelation {

    @EmbeddedId
    private CertificateRelationId id = new CertificateRelationId();

    @Column(name = "relation_type")
    @Enumerated(EnumType.STRING)
    private CertificateRelationType relationType;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    protected OffsetDateTime createdAt;

}

