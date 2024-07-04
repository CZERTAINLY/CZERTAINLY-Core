package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.cryptography.key.KeyUsage;
import com.czertainly.api.model.core.cryptography.tokenprofile.TokenProfileDetailDto;
import com.czertainly.api.model.core.cryptography.tokenprofile.TokenProfileDto;
import com.czertainly.core.service.model.Securable;
import com.czertainly.core.util.DtoMapper;
import com.czertainly.core.util.ObjectAccessControlMapper;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
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
    @ToString.Exclude
    @JsonBackReference
    private TokenInstanceReference tokenInstanceReference;

    @Column(name = "token_instance_ref_uuid")
    private UUID tokenInstanceReferenceUuid;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "usage")
    private String usage;

    public void setTokenInstanceReference(TokenInstanceReference tokenInstanceReference) {
        this.tokenInstanceReference = tokenInstanceReference;
        if (tokenInstanceReference != null) this.tokenInstanceReferenceUuid = tokenInstanceReference.getUuid();
    }

    public List<KeyUsage> getUsage() {
        if(usage == null) return new ArrayList<>();
        return Arrays.stream(
                usage.split(",")
        ).map(
                i -> KeyUsage.valueOf(
                        Integer.parseInt(i)
                )
        ).collect(Collectors.toList());
    }

    public void setUsage(List<KeyUsage> usage) {
        this.usage = usage.stream().map(
                i -> String.valueOf(
                        i.getBitmask()
                )
        ).collect(
                Collectors.joining(",")
        );
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

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        TokenProfile that = (TokenProfile) o;
        return getUuid() != null && Objects.equals(getUuid(), that.getUuid());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
