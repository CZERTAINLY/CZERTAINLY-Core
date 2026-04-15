package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.cryptography.key.KeyState;
import com.czertainly.api.model.core.cryptography.key.KeyUsage;
import com.czertainly.api.model.core.oid.SystemOid;
import com.czertainly.core.util.MetaDefinitions;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Test builder for {@link Certificate} entities.
 * Defaults produce a certificate valid for non-qualified timestamping.
 */
public final class CertificateBuilder {

    private CertificateState state = CertificateState.ISSUED;
    private CertificateValidationStatus validationStatus = CertificateValidationStatus.VALID;
    private boolean archived = false;
    private String extendedKeyUsage = MetaDefinitions.serializeArrayString(List.of(SystemOid.TIME_STAMPING.getOid()));
    private boolean extendedKeyUsageCritical = true;
    private Boolean qcCompliance = null;
    private UUID uuid = UUID.randomUUID();
    private boolean withKey = true;

    public static CertificateBuilder aCertificate() {
        return new CertificateBuilder();
    }

    /** Returns a minimal valid certificate for non-qualified timestamping. */
    public static Certificate valid() {
        return aCertificate().build();
    }

    public CertificateBuilder uuid(UUID uuid) {
        this.uuid = uuid;
        return this;
    }

    public CertificateBuilder state(CertificateState state) {
        this.state = state;
        return this;
    }

    public CertificateBuilder validationStatus(CertificateValidationStatus validationStatus) {
        this.validationStatus = validationStatus;
        return this;
    }

    public CertificateBuilder archived(boolean archived) {
        this.archived = archived;
        return this;
    }

    public CertificateBuilder extendedKeyUsage(List<String> oids) {
        this.extendedKeyUsage = oids.isEmpty() ? null : MetaDefinitions.serializeArrayString(oids);
        return this;
    }

    public CertificateBuilder extendedKeyUsageCritical(boolean extendedKeyUsageCritical) {
        this.extendedKeyUsageCritical = extendedKeyUsageCritical;
        return this;
    }

    public CertificateBuilder qcCompliance(Boolean qcCompliance) {
        this.qcCompliance = qcCompliance;
        return this;
    }

    public CertificateBuilder withoutKey() {
        this.withKey = false;
        return this;
    }

    public Certificate build() {
        Certificate certificate = new Certificate();
        certificate.uuid = uuid;
        certificate.setState(state);
        certificate.setValidationStatus(validationStatus);
        certificate.setArchived(archived);
        certificate.setExtendedKeyUsage(extendedKeyUsage);
        certificate.setExtendedKeyUsageCritical(extendedKeyUsageCritical);
        certificate.setQcCompliance(qcCompliance);

        if (withKey) {
            CryptographicKey key = new CryptographicKey();
            CryptographicKeyItem privateKeyItem = new CryptographicKeyItem();
            privateKeyItem.setType(KeyType.PRIVATE_KEY);
            privateKeyItem.setKeyAlgorithm(KeyAlgorithm.RSA);
            privateKeyItem.setUsage(List.of(KeyUsage.SIGN));
            privateKeyItem.setState(KeyState.ACTIVE);
            privateKeyItem.setKey(key);
            key.setItems(Set.of(privateKeyItem));
            key.setTokenProfile(new TokenProfile());
            certificate.setKey(key);
        }

        return certificate;
    }
}
