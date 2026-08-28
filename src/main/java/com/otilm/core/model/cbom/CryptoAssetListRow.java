package com.otilm.core.model.cbom;

import com.otilm.api.model.core.cryptoasset.CryptographicAssetType;
import com.otilm.api.model.core.cryptoasset.PqcVerdict;
import java.util.UUID;

/**
 * One inventory list row: the columns the list operation serves and nothing else -- never the merge payloads.
 * {@code identityGuard} rides along because the wire's {@code quarantined} flag is derived from it in the service;
 * {@code oid} rides along as the fallback for the contract-required {@code name} on a nameless producer row;
 * {@code occurrenceCount} is the sum of the per-source occurrence counts.
 */
public record CryptoAssetListRow(UUID uuid, String name, String oid, CryptographicAssetType assetType,
        PqcVerdict pqcVerdict, int sourceCount, CryptoAssetIdentityGuard identityGuard, long occurrenceCount) {
}
