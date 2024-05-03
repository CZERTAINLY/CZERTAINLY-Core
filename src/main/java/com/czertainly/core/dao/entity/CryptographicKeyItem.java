package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.cryptography.key.KeyCompromiseReason;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyFormat;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.connector.cryptography.key.value.KeyValue;
import com.czertainly.api.model.core.cryptography.key.*;
import com.czertainly.core.util.CryptographicHelper;
import com.czertainly.core.util.DtoMapper;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "cryptographic_key_item")
@EntityListeners(AuditingEntityListener.class)
public class CryptographicKeyItem extends UniquelyIdentified implements Serializable, DtoMapper<KeyItemDetailDto> {

    @Column(name = "name")
    private String name;

    // TODO: change name of the column to key_uuid
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cryptographic_key_uuid", insertable = false, updatable = false, nullable = false)
    private CryptographicKey cryptographicKey;

    // TODO: change name of the column to key_uuid
    @Column(name = "cryptographic_key_uuid")
    private UUID cryptographicKeyUuid;

    @Column(name = "key_reference_uuid")
    private UUID keyReferenceUuid;

    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private KeyType type;

    // TODO: change name of the column to key_algorithm
    @Column(name = "cryptographic_algorithm")
    @Enumerated(EnumType.STRING)
    private KeyAlgorithm keyAlgorithm;

    @Column(name = "format")
    @Enumerated(EnumType.STRING)
    private KeyFormat format;

    @Column(name = "keyData")
    private String keyData;

    @Column(name = "length")
    private int length;

    @Column(name = "state")
    @Enumerated(EnumType.STRING)
    private KeyState state;

    @Column(name = "usage")
    private String usage;

    @Column(name = "enabled")
    private boolean enabled;

    @Column(name = "fingerprint")
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason")
    private KeyCompromiseReason reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    protected LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
    protected LocalDateTime updatedAt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

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

    public KeyAlgorithm getKeyAlgorithm() {
        return keyAlgorithm;
    }

    public void setKeyAlgorithm(KeyAlgorithm keyAlgorithm) {
        this.keyAlgorithm = keyAlgorithm;
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

    public void setKeyData(KeyFormat keyFormat, KeyValue value) {
        this.keyData = CryptographicHelper.serializeKeyValue(keyFormat, value);
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

    public KeyState getState() {
        return state;
    }

    public void setState(KeyState state) {
        this.state = state;
    }

    public KeyCompromiseReason getReason() {
        return reason;
    }

    public void setReason(KeyCompromiseReason reason) {
        this.reason = reason;
    }

    public List<KeyUsage> getUsage() {
        if (usage == null) return new ArrayList<>();
        return Arrays.stream(
                usage.split(",")
        ).map(
                i -> KeyUsage.valueOf(
                        Integer.valueOf(i)
                )
        ).collect(Collectors.toList());
    }

    public void setUsage(List<KeyUsage> usage) {
        if (usage == null || usage.size() == 0) {
            this.usage = null;
            return;
        }

        this.usage = String.join(
                ",",
                usage.stream().map(
                        i -> String.valueOf(
                                i.getBitmask()
                        )
                ).collect(
                        Collectors.toList()
                )
        );
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("name", name)
                .append("cryptographicKey", cryptographicKey)
                .append("cryptographicKeyUuid", cryptographicKeyUuid)
                .append("type", type)
                .append("keyAlgorithm", keyAlgorithm)
                .append("format", format)
                .append("length", length)
                .append("keyReferenceUuid", keyReferenceUuid)
                .append("uuid", uuid)
                .toString();
    }

    @Override
    public KeyItemDetailDto mapToDto() {
        KeyItemDetailDto dto = new KeyItemDetailDto();
        dto.setName(name);
        dto.setUuid(uuid.toString());
        dto.setKeyReferenceUuid(keyReferenceUuid.toString());
        dto.setKeyAlgorithm(keyAlgorithm);
        dto.setType(type);
        dto.setLength(length);
        dto.setFormat(format);
        dto.setState(state);
        dto.setEnabled(enabled);
        dto.setUsage(getUsage());
        dto.setReason(reason);
        dto.setKeyData(keyData);
        return dto;
    }

    public KeyItemDto mapToSummaryDto() {
        KeyItemDto dto = new KeyItemDto();
        dto.setName(name);
        dto.setUuid(uuid.toString());
        dto.setKeyReferenceUuid(keyReferenceUuid.toString());
        dto.setKeyAlgorithm(keyAlgorithm);
        dto.setType(type);
        dto.setLength(length);
        dto.setFormat(format);
        dto.setState(state);
        dto.setEnabled(enabled);
        dto.setUsage(getUsage());
        dto.setKeyWrapperUuid(cryptographicKey.getUuid().toString());
        dto.setAssociations((cryptographicKey.getItems().size() - 1) + cryptographicKey.getCertificates().size());
        dto.setDescription(cryptographicKey.getDescription());
        if (cryptographicKey.getGroups() != null) {
            dto.setGroups(cryptographicKey.getGroups().stream().map(Group::mapToDto).toList());
        }
        NameAndUuidDto owner = cryptographicKey.getOwner();
        if (owner != null) {
            dto.setOwnerUuid(owner.getUuid());
            dto.setOwner(owner.getName());
        }
        dto.setCreationTime(cryptographicKey.getCreated());
        dto.setTokenInstanceName(cryptographicKey.getTokenInstanceReference().getName());
        dto.setTokenInstanceUuid(cryptographicKey.getTokenInstanceReferenceUuid().toString());
        if (cryptographicKey.getTokenProfile() != null) {
            dto.setTokenProfileName(cryptographicKey.getTokenProfile().getName());
            dto.setTokenProfileUuid(cryptographicKey.getTokenProfile().getUuid().toString());
        }
        return dto;
    }
}
