package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.core.protocol.ProtocolCertificateAssociationsDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.function.TriFunction;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class ProtocolCertificateAssociations extends UniquelyIdentified {

    @Column
    private UUID ownerUuid;

    @Column
    private List<UUID> groupUuids;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<RequestAttributeDto<?>> customAttributes;

    public ProtocolCertificateAssociationsDto mapToDto(TriFunction<AttributeType, UUID, List<RequestAttributeDto<?>>, List<ResponseAttributeDto<?>>> convertAttributesFunc) {
        ProtocolCertificateAssociationsDto dto = new ProtocolCertificateAssociationsDto();
        dto.setCustomAttributes(convertAttributesFunc.apply(AttributeType.CUSTOM, null, customAttributes));
        dto.setGroupUuids(groupUuids);
        dto.setOwnerUuid(ownerUuid);
        return dto;
    }


}
