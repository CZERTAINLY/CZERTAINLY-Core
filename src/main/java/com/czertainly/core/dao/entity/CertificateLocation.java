package com.czertainly.core.dao.entity;

import com.czertainly.core.util.MetaDefinitions;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

@Entity
@Table(name = "certificate_location")
public class CertificateLocation implements Serializable {

    @EmbeddedId
    private CertificateLocationId id = new CertificateLocationId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("locationId")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("certificateId")
    private Certificate certificate;

    @Column(name = "metadata")
    private String metadata;

    @Column(name = "with_key")
    private boolean withKey;

    public CertificateLocation() {}

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

    public Map<String, Object> getMetadata() {
        return MetaDefinitions.deserialize(metadata);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = MetaDefinitions.serialize(metadata);
    }

    public boolean isWithKey() {
        return withKey;
    }

    public void setWithKey(boolean hasPrivateKey) {
        this.withKey = hasPrivateKey;
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
}
