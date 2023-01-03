package com.czertainly.core.dao.entity;

import com.czertainly.api.model.connector.cryptography.enums.CryptographicAlgorithm;
import com.czertainly.api.model.connector.cryptography.enums.KeyFormat;
import com.czertainly.api.model.connector.cryptography.enums.KeyType;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "cryptographic_key_content")
public class CryptographicKeyContent extends UniquelyIdentified implements Serializable {

    @ManyToOne
    @JoinColumn(name = "cryptographic_key_uuid", insertable = false, updatable = false)
    private CryptographicKey cryptographicKey;

    @Column(name = "cryptographic_key_uuid")
    private UUID cryptographicKeyUuid;

    @Column(name = "key_reference_uuid")
    private UUID keyReferenceUuid;

    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private KeyType type;

    @Column(name = "cryptographicAlgorithm")
    @Enumerated(EnumType.STRING)
    private CryptographicAlgorithm cryptographicAlgorithm;

    @Column(name = "format")
    @Enumerated(EnumType.STRING)
    private KeyFormat format;

    @Column(name = "keyData")
    private String keyData;

    @Column(name = "length")
    private int length;

    public CryptographicKey getCryptographicKey() {
        return cryptographicKey;
    }

    public void setCryptographicKey(CryptographicKey cryptographicKey) {
        this.cryptographicKey = cryptographicKey;
        if (cryptographicKey != null) this.cryptographicKeyUuid = cryptographicKey.getUuid();
    }

    public UUID getCryptographicKeyUuid() {
        return cryptographicKeyUuid;
    }

    public void setCryptographicKeyUuid(UUID cryptographicKeyUuid) {
        this.cryptographicKeyUuid = cryptographicKeyUuid;
    }

    public KeyType getType() {
        return type;
    }

    public void setType(KeyType type) {
        this.type = type;
    }

    public CryptographicAlgorithm getCryptographicAlgorithm() {
        return cryptographicAlgorithm;
    }

    public void setCryptographicAlgorithm(CryptographicAlgorithm cryptographicAlgorithm) {
        this.cryptographicAlgorithm = cryptographicAlgorithm;
    }

    public KeyFormat getFormat() {
        return format;
    }

    public void setFormat(KeyFormat format) {
        this.format = format;
    }

    public String getKeyData() {
        return keyData;
    }

    public void setKeyData(String keyData) {
        this.keyData = keyData;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public UUID getKeyReferenceUuid() {
        return keyReferenceUuid;
    }

    public void setKeyReferenceUuid(UUID keyReferenceUuid) {
        this.keyReferenceUuid = keyReferenceUuid;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("cryptographicKey", cryptographicKey)
                .append("cryptographicKeyUuid", cryptographicKeyUuid)
                .append("type", type)
                .append("cryptographicAlgorithm", cryptographicAlgorithm)
                .append("format", format)
                .append("length", length)
                .append("keyReferenceUuid", keyReferenceUuid)
                .append("uuid", uuid)
                .toString();
    }
}
