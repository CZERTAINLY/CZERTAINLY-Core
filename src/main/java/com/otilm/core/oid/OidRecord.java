package com.otilm.core.oid;

import com.otilm.api.model.core.oid.ExtensionValueEncoding;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.List;

@Builder
public record OidRecord(
        @NotNull String displayName,
        String code,
        List<String> altCodes,
        Boolean defaultCritical,
        ExtensionValueEncoding valueEncoding,
        /** True only for a built-in {@code SystemOid} entry; a DB-backed custom row leaves it false. */
        boolean system
) {
    public OidRecord {
        // Records live in the process-wide OID registry, which hands them out through getOidCache. A
        // caller mutating the list it originally passed in would desync the cache from the derived
        // rdnCodeToOid index — the same hazard cacheOidCategory's defensive copy exists to prevent.
        altCodes = altCodes == null ? null : List.copyOf(altCodes);
    }
}
