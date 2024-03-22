package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.cryptography.key.KeyUsage;
import com.czertainly.api.model.core.cryptography.tokenprofile.TokenProfileDetailDto;
import com.czertainly.api.model.core.cryptography.tokenprofile.TokenProfileDto;
import com.czertainly.core.service.model.Securable;
import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.ObjectAccessControlMapper;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Entity
@Table(name = "token_profile")
public class TokenProfile extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<TokenProfileDto>, Securable, ObjectAccessControlMapper<NameAndUuidDto> {

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "token_instance_name")
    private String tokenInstanceName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "token_instance_ref_uuid", insertable = false, updatable = false)
    private TokenInstanceReference tokenInstanceReference;

    @Column(name = "token_instance_ref_uuid")
    private UUID tokenInstanceReferenceUuid;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "usage")
    private String usage;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTokenInstanceName() {
        return tokenInstanceName;
    }

    public void setTokenInstanceName(String tokenInstanceName) {
        this.tokenInstanceName = tokenInstanceName;
    }

    public TokenInstanceReference getTokenInstanceReference() {
        return tokenInstanceReference;
    }

    public void setTokenInstanceReference(TokenInstanceReference tokenInstanceReference) {
        this.tokenInstanceReference = tokenInstanceReference;
        if (tokenInstanceReference != null) this.tokenInstanceReferenceUuid = tokenInstanceReference.getUuid();
    }

    public UUID getTokenInstanceReferenceUuid() {
        return tokenInstanceReferenceUuid;
    }

    public void setTokenInstanceReferenceUuid(UUID tokenInstanceReferenceUuid) {
        this.tokenInstanceReferenceUuid = tokenInstanceReferenceUuid;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public List<KeyUsage> getUsage() {
        if(usage == null) return new ArrayList<>();
        return Arrays.stream(
                usage.split(",")
        ).map(
                i -> KeyUsage.valueOf(
                        Integer.valueOf(i)
                )
        ).collect(Collectors.toList());
    }

    public void setUsage(List<KeyUsage> usage) {
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

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("name", name)
                .append("description", description)
                .append("tokenInstanceName", tokenInstanceName)
                .append("tokenInstanceReference", tokenInstanceReference)
                .append("tokenInstanceReferenceUuid", tokenInstanceReferenceUuid)
                .append("enabled", enabled)
                .append("uuid", uuid)
                .toString();
    }

    @Override
    public TokenProfileDto mapToDto() {
        TokenProfileDto dto = new TokenProfileDto();
        dto.setEnabled(enabled);
        dto.setUuid(uuid.toString());
        dto.setName(name);
        dto.setDescription(description);
        dto.setTokenInstanceName(tokenInstanceName);
        dto.setTokenInstanceUuid(tokenInstanceReferenceUuid.toString());
        dto.setTokenInstanceStatus(tokenInstanceReference.getStatus());
        dto.setUsages(getUsage());
        return dto;
    }

    public TokenProfileDetailDto mapToDetailDto() {
        TokenProfileDetailDto dto = new TokenProfileDetailDto();
        dto.setEnabled(enabled);
        dto.setUuid(uuid.toString());
        dto.setName(name);
        dto.setDescription(description);
        dto.setTokenInstanceName(tokenInstanceName);
        dto.setTokenInstanceUuid(tokenInstanceReferenceUuid.toString());
        dto.setTokenInstanceStatus(tokenInstanceReference.getStatus());
        dto.setUsages(getUsage());
        // Custom Attributes and Metadata should be set in the service
        return dto;
    }

    @Override
    public NameAndUuidDto mapToAccessControlObjects() {
        return new NameAndUuidDto(uuid.toString(), name);
    }
}
