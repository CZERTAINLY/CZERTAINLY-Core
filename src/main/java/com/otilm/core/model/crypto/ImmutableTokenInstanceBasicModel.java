package com.otilm.core.model.crypto;

import com.otilm.api.model.connector.cryptography.enums.TokenInstanceStatus;
import com.otilm.core.dao.entity.TokenInstanceReference;
import java.util.Objects;
import java.util.UUID;

public record ImmutableTokenInstanceBasicModel(UUID uuid, String tokenInstanceUuid, String name,
        TokenInstanceStatus status, String kind, UUID connectorUuid, String connectorName, UUID connectorInterfaceUuid,
        long tokenProfileCount) implements TokenInstanceBasicModel {
    public static ImmutableTokenInstanceBasicModel from(TokenInstanceReference value) {
        Objects.requireNonNull(value, "Token instance is required.");
        return new ImmutableTokenInstanceBasicModel(value.getUuid(), value.getTokenInstanceUuid(), value.getName(),
                value.getStatus(), value.getKind(), value.getConnectorUuid(), value.getConnectorName(),
                value.getConnectorInterfaceUuid(), value.getTokenProfiles().size());
    }

    @Override
    public TokenInstanceBasicModel withNewStatus(TokenInstanceStatus newStatus) {
        return new ImmutableTokenInstanceBasicModel(uuid, tokenInstanceUuid, name, newStatus, kind, connectorUuid,
                connectorName, connectorInterfaceUuid, tokenProfileCount);
    }
}
