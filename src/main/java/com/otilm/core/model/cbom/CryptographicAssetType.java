package com.otilm.core.model.cbom;

/**
 * The kind of cryptographic asset an inventory row describes.
 *
 * <p>
 * The first four values are CycloneDX's own crypto asset types. {@link #UNROUTABLE} is this platform's backstop for a
 * crypto component that carries an {@code assetType} this version does not know, or no {@code cryptoProperties} at all:
 * ingest must store what it was given rather than drop a component it cannot route, because a dropped component is
 * indistinguishable from a component that was never there.
 *
 * <p>
 * Core-local placeholder for interfaces#874, which ratifies this enum in the contract artifact. The constant names are
 * the persisted values, so they must survive the move unchanged.
 */
public enum CryptographicAssetType {
    ALGORITHM,
    CERTIFICATE,
    PROTOCOL,
    RELATED_CRYPTO_MATERIAL,
    UNROUTABLE
}
