package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.core.protocol.ProtocolCertificateAssociationsDto;
import com.czertainly.core.util.DtoMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class ProtocolCertificateAssociation extends UniquelyIdentified implements DtoMapper<ProtocolCertificateAssociationsDto> {

    @Column
    private UUID ownerUuid;

    @Column
    private List<UUID> groupUuids;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<RequestAttributeDto> customAttributes;

    @Override
    public ProtocolCertificateAssociationsDto mapToDto() {
        ProtocolCertificateAssociationsDto dto = new ProtocolCertificateAssociationsDto();
        dto.setCustomAttributes(customAttributes);
        dto.setGroupUuids(groupUuids);
        dto.setOwnerUuid(ownerUuid);
        return dto;
    }


}
