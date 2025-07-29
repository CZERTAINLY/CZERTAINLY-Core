package com.czertainly.core.oid;

import jakarta.validation.constraints.NotNull;

public record OidRecord(
        @NotNull String displayName,
        String code
) {
}
