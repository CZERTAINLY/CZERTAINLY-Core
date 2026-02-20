package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.secret.SecretDetailDto;
import com.czertainly.api.model.core.secret.SecretDto;
import com.czertainly.api.model.core.secret.SecretState;
import com.czertainly.api.model.core.secret.SecretType;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.SQLJoinTableRestriction;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "secret")
@Data
public class Secret extends UniquelyIdentifiedAndAudited {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "source_vault_profile_uuid", nullable = false)
    private UUID sourceVaultProfileUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_vault_profile_uuid", nullable = false, insertable = false, updatable = false)
    @ToString.Exclude
    @JsonBackReference
    private VaultProfile sourceVaultProfile;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "latest_version_uuid", nullable = false, insertable = false, updatable = false)
    @ToString.Exclude
    private SecretVersion latestVersion;

    @Column(name = "latest_version_uuid", nullable = false)
    private UUID latestVersionUuid;

    @OneToMany(mappedBy = "secret", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude
    private Set<SecretVersion> versions = new HashSet<>();

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private SecretType type;

    @Column(name = "state", nullable = false)
    @Enumerated(EnumType.STRING)
    private SecretState state;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "group_association",
            joinColumns = @JoinColumn(name = "object_uuid", referencedColumnName = "uuid", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)),
            inverseJoinColumns = @JoinColumn(name = "group_uuid", insertable = false, updatable = false)
    )
    @SQLJoinTableRestriction("resource = 'SECRET'")
    @ToString.Exclude
    private Set<Group> groups = new HashSet<>();

    @OneToOne(fetch = FetchType.LAZY, mappedBy = "secret")
    @ToString.Exclude
    private OwnerAssociation owner;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "secret_2_sync_vault_profile",
            joinColumns = @JoinColumn(name = "secret_uuid"),
            inverseJoinColumns = @JoinColumn(name = "vault_profile_uuid"))
    @ToString.Exclude
    private Set<VaultProfile> syncVaultProfiles = new HashSet<>();

    public SecretDetailDto mapToDetailDto() {
        SecretDetailDto dto = new SecretDetailDto();
        setCommonFields(dto);
        dto.setSyncVaultProfiles(syncVaultProfiles.stream().map(p -> new NameAndUuidDto(p.getUuid().toString(), p.getName())).toList());
        dto.setCreatedAt(created);
        dto.setUpdatedAt(updated);
        return dto;
    }

    public SecretDto mapToDto() {
        SecretDto dto = new SecretDto();
        setCommonFields(dto);
        return dto;
    }

    private void setCommonFields(SecretDto secretDto) {
        secretDto.setUuid(String.valueOf(uuid));
        secretDto.setName(name);
        secretDto.setDescription(description);
        secretDto.setType(type);
        secretDto.setState(state);
        secretDto.setEnabled(enabled);
        secretDto.setVersion(versions.stream().filter(v -> v.getUuid().equals(latestVersionUuid)).findFirst().map(SecretVersion::getVersion).orElse(0));
        secretDto.setSourceVaultProfile(new NameAndUuidDto(sourceVaultProfile.getUuid().toString(), sourceVaultProfile.getName()));
        secretDto.setOwner(new NameAndUuidDto(owner.getOwnerUuid().toString(), owner.getOwnerUsername()));
        secretDto.setGroups(groups.stream().map(g -> new NameAndUuidDto(g.getUuid().toString(), g.getName())).toList());
    }
}
