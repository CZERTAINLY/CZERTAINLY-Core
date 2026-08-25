package com.otilm.core.cbom.asset;

import com.otilm.core.model.cbom.CryptographicAssetType;

/**
 * The complete input to a cryptographic asset's identity key: the asset type and the nine producer-supplied identity
 * columns, exactly as they are stored.
 *
 * <p>
 * Being a record with no other members is the point. The key must be a function of these fields and of nothing else --
 * no repository, no alias table, no ambient state -- so that two nodes, two releases and two re-ingests of the same
 * document agree on it.
 */
public record CryptoAssetIdentityFields(CryptographicAssetType assetType, String name, String oid,
        String algorithmFamily, String primitive, String parameterSet, String curve, String mode, String padding,
        String variant) {
}
