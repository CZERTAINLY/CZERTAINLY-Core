package com.czertainly.core.dao.entity.signing;

import com.czertainly.core.dao.entity.UniquelyIdentifiedAndAudited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "signing_profile_version")
public class SigningProfileVersion extends UniquelyIdentifiedAndAudited {

    @Column(name = "signing_profile_uuid", nullable = false)
    private UUID signingProfileUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signing_profile_uuid", nullable = false, insertable = false, updatable = false)
    @ToString.Exclude
    private SigningProfile signingProfile;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "scheme_snapshot", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String schemeSnapshot;

    @Column(name = "workflow_snapshot", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String workflowSnapshot;

    @Column(name = "created_at")
    @CreationTimestamp
    private OffsetDateTime createdAt;

    public void setSigningProfile(SigningProfile signingProfile) {
        this.signingProfile = signingProfile;
        this.signingProfileUuid = signingProfile != null ? signingProfile.getUuid() : null;
    }
}
