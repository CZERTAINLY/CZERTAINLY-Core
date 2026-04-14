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

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "signing_record")
public class SigningRecord extends UniquelyIdentifiedAndAudited {

    @Column(name = "name")
    private String name;

    @Column(name = "signing_profile_uuid")
    private UUID signingProfileUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signing_profile_uuid", insertable = false, updatable = false)
    @ToString.Exclude
    private SigningProfile signingProfile;

    @Column(name = "signing_profile_version", nullable = false)
    private Integer signingProfileVersion;

    @Column(name = "signing_time", nullable = false)
    private OffsetDateTime signingTime;

    @Column(name = "created_at")
    @CreationTimestamp
    private OffsetDateTime createdAt;

    @Column(name = "signature_value")
    private byte[] signatureValue;

    public void setSigningProfile(SigningProfile signingProfile) {
        this.signingProfile = signingProfile;
        this.signingProfileUuid = signingProfile != null ? signingProfile.getUuid() : null;
    }
}
