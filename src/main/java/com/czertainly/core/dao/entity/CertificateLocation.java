package com.czertainly.core.dao.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "certificate_location")
public class CertificateLocation implements Serializable {

    @EmbeddedId
    private CertificateLocationId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("locationId")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("certificateId")
    private Certificate certificate;

    @Column(name = "additional_data")
    private String additionalData;

    public CertificateLocation() {}

    public CertificateLocation(Location location, Certificate certificate) {
        this.location = location;
        this.certificate = certificate;
        this.id = new CertificateLocationId(location.getId(), certificate.getId());
    }

    public CertificateLocationId getId() {
        return id;
    }

    public void setId(CertificateLocationId id) {
        this.id = id;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public Certificate getCertificate() {
        return certificate;
    }

    public void setCertificate(Certificate certificate) {
        this.certificate = certificate;
    }

    public String getAdditionalData() {
        return additionalData;
    }

    public void setAdditionalData(String additionalData) {
        this.additionalData = additionalData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass())
            return false;

        CertificateLocation that = (CertificateLocation) o;
        return Objects.equals(location, that.location) &&
                Objects.equals(certificate, that.certificate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(location, certificate);
    }

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
    }

}
