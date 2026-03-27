package com.czertainly.core.dao.entity.signing;

import com.czertainly.api.model.core.signing.digitalsignature.DigitalSignatureDto;
import com.czertainly.api.model.core.signing.digitalsignature.DigitalSignatureListDto;
import com.czertainly.core.dao.entity.UniquelyIdentifiedAndAudited;
import com.czertainly.core.util.DtoMapper;
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
import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "digital_signature")
public class DigitalSignature extends UniquelyIdentifiedAndAudited implements DtoMapper<DigitalSignatureDto> {

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

    @Override
    public DigitalSignatureDto mapToDto() {
        DigitalSignatureDto dto = new DigitalSignatureDto();
        dto.setUuid(uuid.toString());
        dto.setName(name);
        ZonedDateTime signingTimeZoned = signingTime != null ? signingTime.toZonedDateTime() : null;
        dto.setSigningTime(signingTimeZoned);
        ZonedDateTime createdAtZoned = createdAt != null ? createdAt.toZonedDateTime() : null;
        dto.setCreatedAt(createdAtZoned);
        dto.setSignatureValue(signatureValue);
        return dto;
    }

    public void setSigningProfile(SigningProfile signingProfile) {
        this.signingProfile = signingProfile;
        this.signingProfileUuid = signingProfile != null ? signingProfile.getUuid() : null;
    }

    public DigitalSignatureListDto mapToListDto() {
        DigitalSignatureListDto dto = new DigitalSignatureListDto();
        dto.setUuid(uuid.toString());
        dto.setName(name);
        ZonedDateTime signingTimeZoned = signingTime != null ? signingTime.toZonedDateTime() : null;
        dto.setSigningTime(signingTimeZoned);
        ZonedDateTime createdAtZoned = createdAt != null ? createdAt.toZonedDateTime() : null;
        dto.setCreatedAt(createdAtZoned);
        return dto;
    }
}
