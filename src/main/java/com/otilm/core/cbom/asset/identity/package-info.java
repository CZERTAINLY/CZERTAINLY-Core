/**
 * The deterministic half of the ratified cryptographic-asset identity chain: the primitives a stored identity key is
 * built out of.
 *
 * <p>
 * The classes here look like a grab bag of utilities and are not one. They are two layers of a single pipeline, and the
 * reason each exists is that its JDK or platform equivalent would have made a stored key unstable.
 *
 * <ul>
 * <li><b>Byte determinism.</b> {@link com.otilm.core.cbom.asset.identity.AsciiText},
 * {@link com.otilm.core.cbom.asset.identity.IdentityDigests}, {@link com.otilm.core.cbom.asset.identity.PreImageSlot},
 * {@link com.otilm.core.cbom.asset.identity.ValidityTimestamps} and
 * {@link com.otilm.core.cbom.asset.identity.CanonicalJson}. Each deliberately diverges from the obvious JDK call: an
 * ASCII-only fold because Unicode case tables change between versions, a digest that refuses unpaired surrogates
 * because the encoder would silently fold them onto one byte sequence, slot escaping so a value cannot forge a
 * delimiter, epoch folding so two spellings of one instant agree.</li>
 * <li><b>Field reducers.</b> {@link com.otilm.core.cbom.asset.identity.CbomNames},
 * {@link com.otilm.core.cbom.asset.identity.ComponentNames},
 * {@link com.otilm.core.cbom.asset.identity.CertificateDigests},
 * {@link com.otilm.core.cbom.asset.identity.CipherSuites}, {@link com.otilm.core.cbom.asset.identity.Occurrences},
 * {@link com.otilm.core.cbom.asset.identity.MaterialValueDigest} and
 * {@link com.otilm.core.cbom.asset.identity.MaterialRedaction}. Each reduces one producer-controlled field to a keyable
 * token. Redaction is here rather than in a security package because its digest is an input to the material tier, not
 * only a control.</li>
 * </ul>
 *
 * <p>
 * <b>Nothing in production calls them yet, and that is the shape of the split rather than dead code.</b> The two layers
 * that do -- table-driven normalization and the routing that assembles a key -- need the ratified decision tables,
 * which arrive with a generator of their own in core#2168. They land on top of this package and compose it; splitting
 * them off keeps the tables' bulk out of the review of the persistence layer that this change is actually about. Each
 * class here is pinned by its own test, so the layer is reviewable on its own terms before anything keys on it.
 *
 * <p>
 * <b>Do not reuse these as general utilities.</b> Their divergences are the point, and a caller who wants ordinary text
 * handling wants {@code StringUtils} or {@code CertificateUtil} instead. Reaching for {@code AsciiText.strip} because
 * it is nearby gives non-JDK whitespace semantics to code that did not ask for them.
 *
 * <p>
 * The package stays flat on purpose. Splitting it by layer would force
 * {@link com.otilm.core.cbom.asset.identity.CbomNames} and the digest-claim helpers to widen from package-private, and
 * that encapsulation is what keeps the pipeline's vocabulary out of reach of the rest of the platform.
 */
package com.otilm.core.cbom.asset.identity;
