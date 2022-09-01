package com.czertainly.core.dao.entity;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 Embedded class for a composite primary key
 */
@Embeddable
public class CertificateLocationId implements Serializable {

    @Column(name = "location_uuid")
    private UUID locationUuid;

    @Column(name = "certificate_uuid")
    private UUID certificateUuid;

    public CertificateLocationId() {}

    public CertificateLocationId (UUID locationUuid, UUID certificateUuid) {
        this.locationUuid = locationUuid;
        this.certificateUuid = certificateUuid;
    }

    public UUID getLocationUuid() {
        return locationUuid;
    }

    public void setLocationUuid(UUID locationUuid) {
        this.locationUuid = locationUuid;
    }

    public UUID getCertificateUuid() {
        return certificateUuid;
    }

    public void setCertificateUuid(UUID certificateUuid) {
        this.certificateUuid = certificateUuid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass())
            return false;

        CertificateLocationId that = (CertificateLocationId) o;
        return Objects.equals(locationUuid, that.locationUuid) &&
                Objects.equals(certificateUuid, that.certificateUuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationUuid, certificateUuid);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("locationUuid", locationUuid)
                .append("certificateUuid", certificateUuid)
                .toString();
    }
}