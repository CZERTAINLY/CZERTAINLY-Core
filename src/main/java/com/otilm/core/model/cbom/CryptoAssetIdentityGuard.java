package com.otilm.core.model.cbom;

/**
 * The safety rule that forced a cryptographic asset to remain its own row, where a looser reading of the producer's
 * data would have merged it into another.
 *
 * <p>
 * Recorded on the row because the alias repair path has to refuse an alias "where a safety rule caused the split", and
 * that is undecidable unless the row says which rule fired. Deriving it at refusal time from the unindexed merged
 * payload would make a safety check depend on parsing a document whose shape the producer controls.
 *
 * <ul>
 * <li>{@link #REFUTED_CERTIFICATE_DIGEST} -- a certificate whose supplied digest contradicted its other properties, so
 * the digest was not used to key it. Merging it with a digest-keyed row would assert an identity the evidence
 * refuted.</li>
 * <li>{@link #BARE_CN_SUBJECT} -- a certificate identified only by common name, facing rows identified by a full
 * subject DN. Two certificates can share a common name.</li>
 * <li>{@link #REFUTED_OID} -- an OID whose registry meaning contradicted the component's other properties, so the OID
 * was not used to key it.</li>
 * </ul>
 *
 * <p>
 * Core-local; to be reconciled with the vocabulary ratified in core#2070. The constant names are the persisted values.
 */
public enum CryptoAssetIdentityGuard {
    REFUTED_CERTIFICATE_DIGEST,
    BARE_CN_SUBJECT,
    REFUTED_OID
}
