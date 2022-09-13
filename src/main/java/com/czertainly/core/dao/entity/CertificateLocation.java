package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.MetaDefinitions;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Entity
@Table(name = "certificate_location")
public class CertificateLocation implements Serializable {

    @EmbeddedId
    private CertificateLocationId id = new CertificateLocationId();

    @ManyToOne(fetch = FetchType.EAGER)
    @MapsId("locationUuid")
    private Location location;

    @ManyToOne(fetch = FetchType.EAGER)
    @MapsId("certificateUuid")
    private Certificate certificate;

    @Column(name = "metadata")
    private String metadata;

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

    public Map<String, Object> getMetadata() {
        return MetaDefinitions.deserialize(metadata);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = MetaDefinitions.serialize(metadata);
    }

    public List<AttributeDefinition> getPushAttributes() {
        return AttributeDefinitionUtils.deserialize(pushAttributes);
    }

    public void setPushAttributes(List<AttributeDefinition> pushAttributes) {
        this.pushAttributes = AttributeDefinitionUtils.serialize(pushAttributes);
    }

    public List<AttributeDefinition> getCsrAttributes() {
        return AttributeDefinitionUtils.deserialize(csrAttributes);
    }

    public void setCsrAttributes(List<AttributeDefinition> csrAttributes) {
        this.csrAttributes = AttributeDefinitionUtils.serialize(csrAttributes);
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

    @Override
    public String toString() {
        return "CertificateLocation{" +
                "id=" + id +
                ", location=" + location +
                ", certificate=" + certificate +
                ", metadata='" + metadata + '\'' +
                ", pushAttributes='" + pushAttributes + '\'' +
                ", csrAttributes='" + csrAttributes + '\'' +
                ", withKey=" + withKey +
                '}';
    }
}
