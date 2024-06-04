package com.czertainly.core.dao.entity;


import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.core.util.AttributeDefinitionUtils;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "certificate_location")
public class CertificateLocation implements Serializable {

    @EmbeddedId
    private CertificateLocationId id = new CertificateLocationId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("locationUuid")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("certificateUuid")
    private Certificate certificate;

    @Column(name = "push_attributes")
    private String pushAttributes;

    @Column(name = "csr_attributes")
    private String csrAttributes;

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

    public List<DataAttribute> getPushAttributes() {
        return AttributeDefinitionUtils.deserialize(pushAttributes, DataAttribute.class);
    }

    public void setPushAttributes(List<DataAttribute> pushAttributes) {
        this.pushAttributes = AttributeDefinitionUtils.serialize(pushAttributes);
    }

    public List<DataAttribute> getCsrAttributes() {
        return AttributeDefinitionUtils.deserialize(csrAttributes, DataAttribute.class);
    }

    public void setCsrAttributes(List<DataAttribute> csrAttributes) {
        this.csrAttributes = AttributeDefinitionUtils.serialize(csrAttributes);
    }

    public OffsetDateTime getCreated() {return certificate.getCreated();}

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

    @Override
    public String toString() {
        return "CertificateLocation{" +
                "id=" + id +
                ", location=" + location +
                ", certificate=" + certificate +
                ", pushAttributes='" + pushAttributes + '\'' +
                ", csrAttributes='" + csrAttributes + '\'' +
                ", withKey=" + withKey +
                '}';
    }
}
