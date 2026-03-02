package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.attribute.RequestAttribute;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "secret_2_sync_vault_profile")
@Getter
@Setter
public class Secret2SyncVaultProfile {

    @EmbeddedId
    private Secret2SyncVaultProfileId id = new Secret2SyncVaultProfileId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("secretUuid")
    private Secret secret;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("syncVaultProfileUuid")
    private VaultProfile syncProfile;

    @Column(name = "secret_attributes", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<RequestAttribute> secretAttributes;

}
