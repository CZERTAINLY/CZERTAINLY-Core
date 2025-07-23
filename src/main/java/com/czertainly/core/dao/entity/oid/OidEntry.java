package com.czertainly.core.dao.entity.oid;

import com.czertainly.api.model.core.oid.OidCategory;
import com.czertainly.api.model.core.oid.OidEntryDetailResponseDto;
import com.czertainly.api.model.core.oid.OidEntryResponseDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Inheritance
@DiscriminatorColumn
@Getter
@Setter
public class OidEntry {

    @Id
    @Column(name = "oid", nullable = false, updatable = false)
    private String oid;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "description")
    private String description;

    @Column(name = "category", nullable = false)
    @Enumerated(EnumType.STRING)
    private OidCategory category;

    public OidEntryResponseDto mapToDto() {
        OidEntryResponseDto dto = new OidEntryResponseDto();
        dto.setOid(oid);
        dto.setCategory(category);
        dto.setDescription(description);
        dto.setDisplayName(displayName);
        return dto;
    }

    public OidEntryDetailResponseDto mapToDetailDto() {
        OidEntryDetailResponseDto dto = new OidEntryDetailResponseDto();
        dto.setOid(oid);
        dto.setCategory(category);
        dto.setDescription(description);
        dto.setDisplayName(displayName);
        return dto;
    }

}
