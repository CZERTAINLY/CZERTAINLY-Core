package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The normalization rules whose <em>reasons</em> outlive the corpus that found them.
 *
 * <p>
 * Each case pins a measured defect rather than a preference, so a future change that looks like a simplification fails
 * here with the reason attached. The byte-level conformance suite already proves agreement with the reference; what it
 * does not do is say why any individual rule exists, and a rule whose reason is unrecorded is one an optimizer deletes.
 */
class NormalizationRulesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final IdentityTables TABLES = IdentityTables.load();

    private static final AssetNormalizer NORMALIZER = new AssetNormalizer(TABLES);

    private static final CryptoAssetIdentity IDENTITY = new CryptoAssetIdentity(NORMALIZER);

    // ---------------------------------------------------------------- weak-crypto erasure

    /**
     * The defect an inventory must never produce: a weak algorithm merging into a strong one.
     *
     * <p>
     * The digest was once dropped from composite constructions, so these three shared one identity and the MD5 finding
     * disappeared from the estate.
     */
    @Test
    void aDigestIsNeverDroppedFromACompositeConstruction() {
        assertThat(keyOfAlgorithm("MD5withRSA")).isNotEqualTo(keyOfAlgorithm("SHA256withRSA"));
        assertThat(keyOfAlgorithm("HMAC-MD5")).isNotEqualTo(keyOfAlgorithm("HMAC-SHA1"));
        assertThat(keyOfAlgorithm("3DES-CMAC")).isNotEqualTo(keyOfAlgorithm("AES-CMAC"));
    }

    /**
     * Split by digest family, then merged by digest length, is the same erasure one level down.
     *
     * <p>
     * {@code SHA-2} is one family token for four digest lengths, so these produced ONE identity until the secondary
     * token carried the size its own matched spelling names.
     */
    @Test
    void digestLengthsDoNotMergeWithinOneDigestFamily() {
        assertThat(keyOfAlgorithm("SHA256withRSA")).isNotEqualTo(keyOfAlgorithm("SHA384withRSA"));
        assertThat(keyOfAlgorithm("SHA384withRSA")).isNotEqualTo(keyOfAlgorithm("SHA512withRSA"));
    }

    /**
     * A mode token the CycloneDX enum has no value for lives in the residue, and must keep these apart.
     *
     * <p>
     * An earlier version stripped the whole size stoplist and collapsed all five onto one identity.
     */
    @Test
    void modesWithNoEnumValueStillDiscriminate() {
        assertThat(new ArrayList<>(java.util.Set
                .of(keyOfAlgorithm("AES-256"), keyOfAlgorithm("AES-256-XTS"), keyOfAlgorithm("AES-256-SIV"),
                        keyOfAlgorithm("AES-256-OCB"), keyOfAlgorithm("AES-256-EAX"))))
                .hasSize(5);
    }

    // ---------------------------------------------------------------- slot contention

    /**
     * A digit run consumed by one slot must not be consumed again by another.
     *
     * <p>
     * Without the curve half of the stoplist, {@code ECDSA-P-256} reads size 256 out of its own curve name and collides
     * with {@code ECDSA-SHA256}.
     */
    @Test
    void aCurveNameIsNotReadAsAKeySize() {
        assertThat(keyOfAlgorithm("ECDSA-P-256")).isNotEqualTo(keyOfAlgorithm("ECDSA-SHA256"));
        assertThat(normalize("ECDSA-P-256").parameterSet()).isNull();
    }

    /**
     * Addresses are not sizes. Both shapes are witnessed and both fall inside the 64..16384 whitelist, so the whitelist
     * cannot catch them.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "key@ff11be02-d1ac-4887-9c11-000000000000",
            "protocol:tls:localhost:13443",
            "cert 192.168.56.10:636"})
    void anAddressShapedRunIsNotReadAsASize(String name) {
        assertThat(normalize(name).parameterSet()).isNull();
    }

    /** A parameter level below the key-size floor still discriminates, or these three merge. */
    @Test
    void aParameterLevelBelowTheSizeFloorStillDiscriminates() {
        assertThat(new ArrayList<>(java.util.Set
                .of(keyOfAlgorithm("ML-DSA-44"), keyOfAlgorithm("ML-DSA-65"), keyOfAlgorithm("ML-DSA-87")))).hasSize(3);
    }

    /**
     * A name that fully determines its own parameter set takes it, and takes it from the token it names FIRST.
     *
     * <p>
     * The first-match rule is why the intrinsic table's order is load-bearing: an unordered map made
     * {@code X25519/X448} answer 448 instead of 256.
     */
    @ParameterizedTest
    @CsvSource({"Ed25519, 256", "X25519, 256", "Ed448, 456", "X448, 448", "X25519/X448, 256", "Ed25519/Ed448, 256"})
    void anIntrinsicSizeComesFromTheTokenNamedFirst(String name, int expected) {
        assertThat(normalize(name).parameterSet()).isEqualTo(expected);
    }

    /** An "RSA-256" key is absurd: for the RSA schemes the slot means a key size, so a digest length is not it. */
    @Test
    void aDigestLengthIsNotAKeySizeForASignatureScheme() {
        assertThat(normalize("SHA512withRSA").parameterSet()).isNull();
    }

    // ---------------------------------------------------------------- corroboration

    /**
     * A producer-declared family outside the registry contributes nothing rather than being keyed verbatim.
     *
     * <p>
     * Measured, 11 corpus assets declare such values and 9 same-name groups split because the declaration used to be
     * taken at face value.
     */
    @Test
    void anUnvocabulariedDeclarationDefersToTheName() {
        NormalizedAsset asset = normalize(algorithmComponent("AES-256-GCM", "{\"algorithmFamily\":\"Hybrid-KEM\"}"));

        assertThat(asset.family()).isEqualTo("AES");
        assertThat(asset.familySource()).contains("declaration unvocabularied");
    }

    /** A case variant of a legal token means the registry's token, not a new one. */
    @Test
    void aFoldedDeclarationResolvesToTheRegistrySpelling() {
        assertThat(normalize(algorithmComponent("x", "{\"algorithmFamily\":\"aes\"}")).family()).isEqualTo("AES");
    }

    /**
     * Subsumption lets a concrete token win without refuting the arc.
     *
     * <p>
     * Treating {@code EC} against {@code ECDSA} as a contradiction would discard the curve the arc supplies and break
     * 1.6/1.7 parity, because 1.6 has no curve field at all.
     */
    @Test
    void aBroadDeclarationYieldsToTheConcreteNameWithoutRefutingTheArc() {
        NormalizedAsset asset = normalize(algorithmComponent("RSAES-OAEP", "{\"algorithmFamily\":\"RSA\"}"));

        assertThat(asset.family()).isEqualTo("RSAES-OAEP");
        assertThat(asset.oidConflict()).isFalse();
    }

    /** A cipher suite derives no family: reducing it to its bulk cipher throws away key exchange and authentication. */
    @Test
    void aCipherSuiteNameDerivesNoFamily() {
        NormalizedAsset asset = normalize("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256");

        assertThat(asset.family()).isNull();
        assertThat(asset.familySource()).isEqualTo("cipher-suite name");
        assertThat(keyOfAlgorithm("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256"))
                .isNotEqualTo(keyOfAlgorithm("TLS_ECDHE_RSA_WITH_ARIA_128_GCM_SHA256"));
    }

    // ---------------------------------------------------------------- what is not keyed

    /**
     * {@code primitive} is derived and stored but never keyed.
     *
     * <p>
     * Three producers describing one RSA-2048 emit {@code signature}, {@code pke} and {@code kem}. Keying it produced
     * 434 keys where 399 are correct, with 426 assets in groups that disagreed with themselves.
     */
    @Test
    void theProducersOpinionOfWhatAKeyIsForDoesNotSplitIt() {
        String asSignature = keyOf(algorithmComponent("RSA-2048", "{\"primitive\":\"signature\"}"));
        String asPke = keyOf(algorithmComponent("RSA-2048", "{\"primitive\":\"pke\"}"));
        String asKem = keyOf(algorithmComponent("RSA-2048", "{\"primitive\":\"kem\"}"));

        assertThat(asSignature).isEqualTo(asPke).isEqualTo(asKem);
        assertThat(normalize(algorithmComponent("RSA-2048", "{\"primitive\":\"pke\"}")).primitive())
                .describedAs("still derived and stored, because the inventory filters on it")
                .isEqualTo("pke");
    }

    /**
     * The OID is in no tuple, so supplying one cannot move a key.
     *
     * <p>
     * It is optional in both schema versions, inconsistently specific and demonstrably sometimes wrong: for one AES-GCM
     * algorithm three producers emit no OID, the leaf arc and the container arc -- three keys for one algorithm if it
     * were admitted.
     */
    @Test
    void supplyingAnOidDoesNotChangeAnIdentity() {
        assertThat(keyOf(algorithmComponent("AES-256-GCM", "{}", "\"oid\":\"2.16.840.1.101.3.4.1.46\",")))
                .isEqualTo(keyOf(algorithmComponent("AES-256-GCM", "{}")));
    }

    /** An AE mode resolves a disagreement two producers can legitimately have about one construction. */
    @Test
    void anAuthenticatedEncryptionModeFoldsACipherPrimitive() {
        assertThat(normalize(algorithmComponent("AES-128-GCM", "{\"primitive\":\"block-cipher\"}")).primitive())
                .isEqualTo("ae");
        assertThat(normalize(algorithmComponent("SHA-256", "{\"primitive\":\"hash\",\"mode\":\"GCM\"}")).primitive())
                .describedAs("a digest is never relabelled by a stray mode token")
                .isEqualTo("hash");
    }

    // ---------------------------------------------------------------- sentinels and robustness

    /**
     * A producer's {@code unknown} is absence, not a value.
     *
     * <p>
     * Measured, the {@code mode} field is populated on four corpus assets and reads {@code unknown} every time, while
     * the real mode sits in {@code parameterSetIdentifier}. A stored {@code unknown} would split the asset from every
     * producer that simply omits the field.
     */
    @ParameterizedTest
    @ValueSource(strings = {"unknown", "n/a", "N/A", "-", "0.0.0.0"})
    void aSentinelIsTreatedAsAbsent(String sentinel) {
        assertThat(TABLES.isSentinel(sentinel)).isTrue();
    }

    @Test
    void theRealModeIsFoundWhereTheProducerActuallyPutIt() {
        NormalizedAsset asset = normalize(
                algorithmComponent("AES-256", "{\"mode\":\"unknown\",\"parameterSetIdentifier\":\"GCM\"}"));

        assertThat(asset.mode()).isEqualTo("GCM");
    }

    /**
     * A component with no {@code cryptoProperties} is not skipped: it keys on the backstop tier WITH its name.
     *
     * <p>
     * Without the name in the key, the projection digest of an absent properties object is the same for every such
     * component, so every broken asset in the estate collapses into one row whose payload is whichever arrived first --
     * an over-merge, the direction the prefer-a-visible-split rule forbids.
     */
    @Test
    void anUnroutableComponentKeysOnItsNameRatherThanBeingSkipped() {
        String first = keyOf(read("{\"type\":\"cryptographic-asset\",\"name\":\"broken-one\"}"));
        String second = keyOf(read("{\"type\":\"cryptographic-asset\",\"name\":\"broken-two\"}"));

        assertThat(first).isNotEqualTo(second);
        assertThat(IDENTITY.of(read("{\"type\":\"cryptographic-asset\",\"name\":\"broken-one\"}")).step())
                .isEqualTo("backstop:unknown-type");
    }

    /** An assetType spelled the way a real producer spells it still routes. */
    @ParameterizedTest
    @CsvSource({
            "related-crypto-material, related-crypto-material",
            "relatedCryptoMaterial, related-crypto-material",
            "algorithm, algorithm",
            "certificate, certificate",
            "protocol, protocol"})
    void aCamelCasedAssetTypeStillRoutes(String spelled, String routed) {
        assertThat(NORMALIZER.normalizeAssetType(spelled)).isEqualTo(routed);
    }

    @Test
    void anAssetTypeTheSpecificationDoesNotKnowIsUnroutableRatherThanFatal() {
        assertThat(NORMALIZER.normalizeAssetType("quantum-widget")).isNull();
    }

    // ---------------------------------------------------------------- helpers

    private static NormalizedAsset normalize(String name) {
        return normalize(algorithmComponent(name, "{}"));
    }

    private static NormalizedAsset normalize(JsonNode component) {
        return NORMALIZER.normalize(component).asset();
    }

    private static String keyOfAlgorithm(String name) {
        return keyOf(algorithmComponent(name, "{}"));
    }

    private static String keyOf(JsonNode component) {
        return IDENTITY.of(component).key();
    }

    private static JsonNode algorithmComponent(String name, String algorithmProperties) {
        return algorithmComponent(name, algorithmProperties, "");
    }

    private static JsonNode algorithmComponent(String name, String algorithmProperties, String extraProperties) {
        return read("{\"type\":\"cryptographic-asset\",\"name\":" + quote(name) + ",\"cryptoProperties\":{"
                + extraProperties + "\"assetType\":\"algorithm\",\"algorithmProperties\":" + algorithmProperties
                + "}}");
    }

    private static String quote(String value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(value, e);
        }
    }

    private static JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("test fixture is not JSON: " + json, e);
        }
    }
}
