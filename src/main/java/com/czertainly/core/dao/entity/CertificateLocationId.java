package com.czertainly.core.dao.entity;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/**
 Embedded class for a composite primary key
 */
@Embeddable
public class CertificateLocationId implements Serializable {

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "certificate_id")
    private Long certificateId;

    public CertificateLocationId() {}

    public CertificateLocationId (Long locationId, Long certificateId) {
        this.locationId = locationId;
        this.certificateId = certificateId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public Long getCertificateId() {
        return certificateId;
    }

    public void setCertificateId(Long certificateId) {
        this.certificateId = certificateId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass())
            return false;

        CertificateLocationId that = (CertificateLocationId) o;
        return Objects.equals(locationId, that.locationId) &&
                Objects.equals(certificateId, that.certificateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationId, certificateId);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("locationId", locationId)
                .append("certificateId", certificateId)
                .toString();
    }
}