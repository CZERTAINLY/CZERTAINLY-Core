package com.czertainly.core.dao.entity;

import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cryptography.key.*;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.DtoMapper;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "cryptographic_key")
public class CryptographicKey extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<KeyDto> {

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @ManyToOne
    @JoinColumn(name = "token_profile_uuid", insertable = false, updatable = false)
    private TokenProfile tokenProfile;

    @Column(name = "token_profile_uuid")
    private UUID tokenProfileUuid;

    @ManyToOne
    @JoinColumn(name = "token_instance_uuid", insertable = false, updatable = false)
    private TokenInstanceReference tokenInstanceReference;

    @Column(name = "token_instance_uuid")
    private UUID tokenInstanceReferenceUuid;

    @Column(name = "attributes")
    private String attributes;

    @Column(name = "owner")
    private String owner;

    @ManyToOne
    @JoinColumn(name = "group_uuid", insertable = false, updatable = false)
    private Group group;

    @Column(name = "group_uuid")
    private UUID groupUuid;

    @JsonBackReference
    @OneToMany(mappedBy = "cryptographicKey", cascade = CascadeType.ALL)
    private Set<CryptographicKeyItem> items = new HashSet<>();

    @JsonBackReference
    @OneToMany(mappedBy = "key")
    private Set<Certificate> certificates = new HashSet<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public Set<CryptographicKeyItem> getItems() {
        return items;
    }

    public void setItems(Set<CryptographicKeyItem> items) {
        this.items = items;
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

    public String getAttributes() {
        return attributes;
    }

    public void setAttributes(String attributes) {
        this.attributes = attributes;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
        if (group != null) this.groupUuid = group.getUuid();
    }

    public UUID getGroupUuid() {
        return groupUuid;
    }

    public void setGroupUuid(UUID groupUuid) {
        this.groupUuid = groupUuid;
    }

    // Get the list of items for the key
    public List<KeyItemDetailDto> getKeyItems() {
        return items.stream().map(CryptographicKeyItem::mapToDto).collect(Collectors.toList());
    }

    // Get the list of items for the key
    public List<KeyItemDto> getKeyItemsSummary() {
        return items.stream().map(CryptographicKeyItem::mapToSummaryDto).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("name", name)
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
        dto.setCreationTime(created);
        if (tokenProfile != null) {
            dto.setTokenProfileName(tokenProfile.getName());
            dto.setTokenProfileUuid(tokenProfile.getUuid().toString());
        }
        dto.setTokenInstanceName(tokenInstanceReference.getName());
        dto.setTokenInstanceUuid(tokenInstanceReferenceUuid.toString());
        if(group != null ) dto.setGroup(group.mapToDto());
        dto.setOwner(owner);
        dto.setItems(getKeyItemsSummary());
        dto.setAssociations((items.size() -1 ) + certificates.size());
        return dto;
    }

    public KeyDetailDto mapToDetailDto() {
        KeyDetailDto dto = new KeyDetailDto();
        dto.setName(name);
        dto.setUuid(uuid.toString());
        dto.setDescription(description);
        dto.setCreationTime(created);
        if (tokenProfile != null) {
            dto.setTokenProfileName(tokenProfile.getName());
            dto.setTokenProfileUuid(tokenProfile.getUuid().toString());
        }
        dto.setTokenInstanceName(tokenInstanceReference.getName());
        dto.setTokenInstanceUuid(tokenInstanceReferenceUuid.toString());
        dto.setItems(getKeyItems());
        dto.setOwner(owner);
        dto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(
                AttributeDefinitionUtils.deserialize(attributes, DataAttribute.class)
        ));
        if (group != null) {
            dto.setGroup(group.mapToDto());
        }
        if(certificates != null && !certificates.isEmpty()) {
            dto.setAssociations(certificates.stream().map(e -> {
                KeyAssociationDto keyAssociationDto = new KeyAssociationDto();
                keyAssociationDto.setName(e.getCommonName());
                keyAssociationDto.setUuid(e.getUuid().toString());
                keyAssociationDto.setResource(Resource.CERTIFICATE);
                return keyAssociationDto;
            }).collect(Collectors.toList()));
        }
        return dto;
    }
}
