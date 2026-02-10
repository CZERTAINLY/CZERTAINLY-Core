package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.vault.VaultInstanceDetailDto;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.UUID;

@Entity
@Table(name = "vault_instance")
@Data
@EqualsAndHashCode(callSuper = true)
public class VaultInstance extends UniquelyIdentifiedAndAudited {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "connector_uuid", nullable = false)
    private UUID connectorUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_uuid", insertable = false, updatable = false)
    @ToString.Exclude
    private Connector connector;


    public VaultInstanceDetailDto mapToDetailDto() {
        VaultInstanceDetailDto dto = new VaultInstanceDetailDto();
        dto.setUuid(uuid.toString());
        dto.setName(name);
        dto.setDescription(description);
        dto.setConnectorUuid(connectorUuid);
        return dto;
    }
}
