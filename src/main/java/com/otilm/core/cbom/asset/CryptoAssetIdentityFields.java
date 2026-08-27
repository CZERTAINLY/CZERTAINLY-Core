package com.otilm.core.cbom.asset;

import com.otilm.api.model.core.cryptoasset.CryptographicAssetType;

/**
 * The complete input to a cryptographic asset's identity key: the asset type and the nine producer-supplied identity
 * columns, as the producer wrote them.
 *
 * <p>
 * Being a record with no other members is the point. The key must be a function of these fields and of nothing else --
 * no repository, no alias table, no ambient state -- so that two nodes, two releases and two re-ingests of the same
 * document agree on it.
 *
 * <p>
 * What gets <em>stored</em> is {@link #normalized()}, not this. The two differ, and the difference is deliberate: an
 * asset row is a deduplicated view over every producer that reported it, so it has no single raw spelling to hold. The
 * producers' own spellings live per source, in the retained payloads, which is where the contract serves them.
 */
public record CryptoAssetIdentityFields(CryptographicAssetType assetType, String name, String oid,
        String algorithmFamily, String primitive, String parameterSet, String curve, String mode, String padding,
        String variant) {

    /**
     * The canonical form the platform stores: every producer-text field folded by the identity normalizer, and a field
     * that is blank after folding stored as absent rather than as whitespace.
     *
     * <p>
     * Storing this rather than the raw input is what makes a filter answer independent of ingest order. The key is
     * computed over the folded fields, so {@code "ECDSA"}, {@code " ecdsa "} and a fullwidth spelling all land on one
     * row -- but the row's own columns used to be reassigned from whichever producer synced last, so an {@code EQUALS}
     * predicate matched or missed depending on sync order, and a field that one producer omitted and another sent as
     * {@code "  "} moved the row in and out of an {@code EMPTY} result set. Nine of the fourteen crypto-asset filter
     * fields were exposed to that.
     *
     * <p>
     * It also makes the stored row sufficient to re-derive its own key. The staleness sweep re-keys rows whose rule-set
     * version has fallen behind, and it can only read the columns; stored-as-keyed turns that from a coincidence into
     * an invariant.
     *
     * <p>
     * {@code assetType} is passed through: it is this platform's own enum constant, already canonical, and running the
     * producer-text normalizer over it would imply it is producer text.
     */
    public CryptoAssetIdentityFields normalized() {
        return new CryptoAssetIdentityFields(assetType, CryptoAssetIdentityCalculator.normalize(name),
                CryptoAssetIdentityCalculator.normalize(oid), CryptoAssetIdentityCalculator.normalize(algorithmFamily),
                CryptoAssetIdentityCalculator.normalize(primitive),
                CryptoAssetIdentityCalculator.normalize(parameterSet), CryptoAssetIdentityCalculator.normalize(curve),
                CryptoAssetIdentityCalculator.normalize(mode), CryptoAssetIdentityCalculator.normalize(padding),
                CryptoAssetIdentityCalculator.normalize(variant));
    }
}
