package com.otilm.core.model.crypto;

import com.otilm.api.model.connector.cryptography.enums.TokenInstanceStatus;
import com.otilm.api.model.core.cryptography.key.KeyUsage;
import com.otilm.core.dao.entity.TokenProfile;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable token-profile snapshot without persistence associations. */
public record ImmutableTokenProfileFullModel(UUID uuid, String name, String description, String tokenInstanceName,
        UUID tokenInstanceReferenceUuid, Boolean enabled, List<KeyUsage> usages,
        TokenInstanceStatus tokenInstanceStatus) {

    public ImmutableTokenProfileFullModel {
        usages = usages == null ? List.of() : List.copyOf(usages);
    }

    public static ImmutableTokenProfileFullModel from(TokenProfile tokenProfile) {
        Objects.requireNonNull(tokenProfile, "Token profile is required.");
        TokenInstanceStatus status = tokenProfile.getTokenInstanceReference() == null
                ? null
                : tokenProfile.getTokenInstanceReference().getStatus();
        return new ImmutableTokenProfileFullModel(tokenProfile.getUuid(), tokenProfile.getName(),
                tokenProfile.getDescription(), tokenProfile.getTokenInstanceName(),
                tokenProfile.getTokenInstanceReferenceUuid(), tokenProfile.getEnabled(), tokenProfile.getUsage(),
                status);
    }
}
