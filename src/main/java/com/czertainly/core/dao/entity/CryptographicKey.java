package com.czertainly.core.dao.entity;

import com.czertainly.api.model.connector.cryptography.enums.CryptographicAlgorithm;
import com.czertainly.api.model.core.cryptography.key.KeyDetailDto;
import com.czertainly.api.model.core.cryptography.key.KeyDto;
import com.czertainly.core.util.DtoMapper;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "cryptographic_key")
public class CryptographicKey extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<KeyDto> {
    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "cryptographic_algorithm")
    @Enumerated(EnumType.STRING)
    private CryptographicAlgorithm cryptographicAlgorithm;

    @ManyToOne
    @JoinColumn(name = "token_profile_uuid", insertable = false, updatable = false)
    private TokenProfile tokenProfile;

    @Column(name = "token_profile_uuid")
    private UUID tokenProfileUuid;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CryptographicAlgorithm getCryptographicAlgorithm() {
        return cryptographicAlgorithm;
    }

    public void setCryptographicAlgorithm(CryptographicAlgorithm cryptographicAlgorithm) {
        this.cryptographicAlgorithm = cryptographicAlgorithm;
    }

    public TokenProfile getTokenProfile() {
        return tokenProfile;
    }

    public void setTokenProfile(TokenProfile tokenProfile) {
        this.tokenProfile = tokenProfile;
        if (tokenProfile != null) this.tokenProfileUuid = tokenProfile.getUuid();
    }

    public UUID getTokenProfileUuid() {
        return tokenProfileUuid;
    }

    public void setTokenProfileUuid(UUID tokenProfileUuid) {
        this.tokenProfileUuid = tokenProfileUuid;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("name", name)
                .append("cryptographicAlgorithm", cryptographicAlgorithm)
                .append("tokenProfile", tokenProfile)
                .append("tokenProfileUuid", tokenProfileUuid)
                .append("uuid", uuid)
                .toString();
    }

    @Override
    public KeyDto mapToDto() {
        KeyDto dto = new KeyDto();
        dto.setName(name);
        dto.setUuid(uuid.toString());
        dto.setDescription(description);
        dto.setCryptographicAlgorithm(cryptographicAlgorithm);
        return dto;
    }

    public KeyDetailDto mapToDetailDto() {
        KeyDetailDto dto = new KeyDetailDto();
        dto.setName(name);
        dto.setUuid(uuid.toString());
        dto.setDescription(description);
        dto.setCryptographicAlgorithm(cryptographicAlgorithm);
        return dto;
    }
}
