package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import java.time.Duration;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

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
     * An uncapped curve field cannot stall ingest, and the alternatives it names still separate.
     *
     * <p>
     * Only the component NAME is length-capped, so a producer's {@code ellipticCurve} is unbounded text. The separator
     * pattern used to carry {@code \s*} on both sides, which made the split quadratic: 16 000 spaces took 6.8s and a
     * megabyte took hours, in a field reached on every EC-bearing component. The bound is generous because it is
     * guarding against a quadratic blow-up, not measuring throughput.
     */
    @Test
    void anUncappedCurveFieldIsSplitInLinearTime() {
        String pathological = " ".repeat(1_000_000);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> NORMALIZER.canonicalCurves(pathological));
    }

    @ParameterizedTest
    @CsvSource({
            "X25519/X448,other/Curve25519+other/Curve448",
            "'P-256, P-384',secg/secp256r1+secg/secp384r1",
            "P-256 or P-384,secg/secp256r1+secg/secp384r1",
            "'  P-256 ,  P-384  ',secg/secp256r1+secg/secp384r1"})
    void theSeparatorAloneNamesTheAlternatives(String raw, String expected) {
        assertThat(NORMALIZER.canonicalCurves(raw))
                .describedAs("whitespace around the separator is the part that never mattered")
                .isEqualTo(expected);
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

    // ---------------------------------------------------------------- sub-delimiters and dropped slots (core#2165)

    /**
     * Neither validity value can forge the boundary between them.
     *
     * <p>
     * R15 escapes {@code |} inside a slot and the two {@code CRT|C} validity slots were joined raw. Reachable on
     * schema-valid input, because an unparseable validity is keyed on the producer's own string.
     */
    @Test
    void aValidityCannotForgeTheSlotBoundaryBesideIt() {
        assertThat(keyOf(certificateComponent("a|b", "c"))).isNotEqualTo(keyOf(certificateComponent("a", "b|c")));
    }

    /**
     * A credential in an occurrence location reaches neither the detector's input nor a served note.
     *
     * <p>
     * The key path and the stored evidence both strip user-info through {@code Occurrences.sanitizeLocation}; the
     * case-risk detector re-read the component's raw {@code location} instead, so the password sat in
     * {@link NormalizedAsset#keyedCaseValues} and the R12 note named characters that only the query string carried. The
     * detector now sees the strings the tier actually hashed, which is the same fix as reporting only what was keyed.
     */
    @Test
    void anOccurrenceCredentialReachesNeitherTheDetectorNorANote() {
        NormalizedAsset asset = IDENTITY.of(protocolAt("tcp://user:p\u00E4ssword@host:443/p?token=\u00C4")).asset();

        assertThat(asset.keyedCaseValues())
                .describedAs("asserted positively first, so the exclusion below cannot pass on an empty list")
                .anyMatch(value -> value.contains("tcp://host:443/p"))
                .noneMatch(value -> value.contains("ssword"));
        assertThat(asset.notes()).noneMatch(note -> note.startsWith("R12:"));
    }

    /**
     * The detector still fires on what survives sanitization, which is what says the fix narrowed rather than blinded.
     *
     * <p>
     * A path is kept where user-info and the query are not, so a non-ASCII cased path is genuinely keyed unfolded and
     * R12 is genuinely owed. Feeding the detector the sanitized string must not cost that.
     */
    @Test
    void aCaseRiskInTheKeyedPathStillSurfaces() {
        NormalizedAsset asset = IDENTITY.of(protocolAt("tcp://host:443/p\u00F6th")).asset();

        assertThat(asset.asciiCaseRisk()).containsExactly("\u00F6");
        assertThat(asset.notes()).anyMatch(note -> note.startsWith("R12:"));
    }

    /**
     * Neither validity value can forge the boundary between them inside the composite either.
     *
     * <p>
     * The second site of the class {@code aValidityCannotForgeTheSlotBoundaryBesideIt} closed on {@code CRT|C}: the
     * {@code CRT|D} composite joined subject, issuer, both validities and the public-key slot with a raw {@code |}
     * before hashing, so the {@code |claimed} marker an observation appends was forgeable from a validity. The
     * composite escapes {@code %} as well as {@code |}, because escaping one side alone preserves the collision.
     */
    @Test
    void aValidityCannotForgeTheBoundaryInsideTheDnComposite() {
        assertThat(keyOf(dnCompositeComponent("a|b", "c"))).isNotEqualTo(keyOf(dnCompositeComponent("a", "b|c")));
        assertThat(keyOf(dnCompositeComponent("a%7Cb", "c"))).isNotEqualTo(keyOf(dnCompositeComponent("a|b", "c")));
    }

    /**
     * A whitespace-only fingerprint content is an absent one, so the tier below it still discriminates.
     *
     * <p>
     * The content gate tested emptiness with {@code isEmpty}, so {@code content: " "} passed it and every such
     * component keyed {@code MAT|<kind>|F|unknown:%20}: one row for every material of that type whose producer wrote a
     * space, with the tier below never reached. Falling through, the two spellings key apart on the payload rather than
     * together on a claim that says nothing. Costs nothing: no ratified vector and no corpus row carries a blank
     * content.
     */
    @Test
    void aBlankFingerprintContentIsAnAbsentOne() {
        assertThat(IDENTITY.of(materialWithFingerprint("sha-256", " ")).step())
                .describedAs("a claim of one space is no claim, so the fingerprint tier does not answer")
                .isNotEqualTo("mat:fingerprint");
        assertThat(IDENTITY.of(materialWithFingerprint("sha-256", " ")).preImage()).doesNotContain("%20");
        assertThat(keyOf(materialWithFingerprint("sha-256", " ")))
                .describedAs(
                        "the tier that answers instead keys on the payload, which differs between the two spellings")
                .isNotEqualTo(keyOf(materialWithFingerprint("sha-256", "")));
    }

    /**
     * Neither half of a material fingerprint can forge the {@code :} between them.
     *
     * <p>
     * The second site of the class {@code CertificateDigests.claim} closed on the certificate side: the material
     * fingerprint kept a bare join, so an algorithm carrying a colon spelled the same claim as a content carrying one.
     */
    @Test
    void aFingerprintAlgorithmCannotForgeItsOwnSeparator() {
        assertThat(keyOf(materialWithFingerprint("sha-256:aabbcc", "dd")))
                .isNotEqualTo(keyOf(materialWithFingerprint("sha-256", "aabbcc:dd")));
    }

    /**
     * A blank algorithm states no algorithm, which is what an absent one already means.
     */
    @Test
    void aBlankFingerprintAlgorithmIsAnAbsentOne() {
        assertThat(keyOf(materialWithFingerprint("", "aabbcc")))
                .isEqualTo(keyOf(read("{\"type\":\"cryptographic-asset\",\"cryptoProperties\":{\"assetType\":"
                        + "\"relatedCryptoMaterial\",\"relatedCryptoMaterialProperties\":{\"type\":\"public-key\","
                        + "\"fingerprint\":{\"content\":\"aabbcc\"}}}}")));
    }

    /**
     * A fingerprint's content is text or it is nothing.
     *
     * <p>
     * {@code asText()} renders a number and a boolean, so {@code content: 10} keyed identically to {@code "10"} and two
     * producers stating different things landed on one row. The certificate side of the same class already gates on the
     * node type.
     */
    @Test
    void aNonTextualFingerprintContentDoesNotKeyAsItsRendering() {
        assertThat(keyOf(read("{\"type\":\"cryptographic-asset\",\"cryptoProperties\":{\"assetType\":"
                + "\"relatedCryptoMaterial\",\"relatedCryptoMaterialProperties\":{\"type\":\"public-key\","
                + "\"fingerprint\":{\"alg\":\"sha-256\",\"content\":10}}}}")))
                .isNotEqualTo(keyOf(materialWithFingerprint("sha-256", "10")));
    }

    /**
     * The protocol occurrence tier carries the version it holds.
     *
     * <p>
     * The tier is reached with a version in hand whenever the name token is empty, and the slot was emitted empty -- so
     * an SSL 3.0 endpoint and a TLS 1.3 endpoint at one location shared an identity, which is the hazard the version
     * tier exists to prevent, one tier down.
     */
    @Test
    void aVersionedNamelessProtocolDoesNotMergeAcrossVersions() {
        assertThat(IDENTITY.of(protocolAtOneLocation("1.3")).step()).isEqualTo("prt:type+occurrence");
        assertThat(keyOf(protocolAtOneLocation("1.3"))).isNotEqualTo(keyOf(protocolAtOneLocation("3.0")));
    }

    /**
     * The bytes of the three pre-image shapes this change moved, pinned where no ratified vector covers them.
     *
     * <p>
     * The 537 generated vectors are unmoved -- measured, and the suite proves it -- because none of them carries a
     * validity needing an escape, a fingerprint claim containing a colon, or a versioned nameless protocol at a
     * location. A third implementation cannot reproduce a byte it has never been shown, so the pre-images are pinned
     * here until {@code make_key_vectors.py} regenerates the vector set from a corpus that exercises them.
     */
    @Test
    void theMovedPreImagesArePinnedByBytes() {
        assertThat(IDENTITY.of(certificateComponent("a|b", "c")).preImage())
                .describedAs("R15 escapes the pipe inside each validity slot")
                .isEqualTo("CRT|C|v1|2.5.4.3=one|a%7Cb|c|cert");
        assertThat(IDENTITY.of(materialWithFingerprint("sha-256:a", "b")).preImage())
                .describedAs("the claim's own colon escapes to %3A and the outer slot escapes that percent again")
                .isEqualTo("MAT|public-key|F|sha-256%253Aa:b");
        assertThat(IDENTITY.of(protocolAtOneLocation("1.3")).preImage())
                .describedAs("the version slot is filled, the name slot empty, the occurrence digest last")
                .isEqualTo("PRT|tls|1.3||" + IdentityDigests.sha256Hex("host:443##"));
    }

    /**
     * A low-entropy asset's fingerprint now keys the fingerprint tier, which is a key move worth pinning.
     *
     * <p>
     * While one payload served identity and storage, such an asset's {@code fingerprint} was dropped before
     * {@code material()} could read it, so the row fell to {@code mat:backstop}. The keyed payload keeps it, so the row
     * keys on {@code mat:fingerprint} instead. That is toward the reference -- the specification's
     * {@code MAT|<type>|F|...} carries no low-entropy exception -- and it costs 0 corpus rows, because all 453 corpus
     * fingerprints sit on publishable types. No corpus row means the snapshot instrument is silent here by luck of the
     * corpus rather than by construction, which is why it is pinned by hand.
     */
    @Test
    void aLowEntropyFingerprintKeysTheFingerprintTier() {
        JsonNode component = read("{\"type\":\"cryptographic-asset\",\"cryptoProperties\":{\"assetType\":"
                + "\"relatedCryptoMaterial\",\"relatedCryptoMaterialProperties\":{\"type\":\"password\","
                + "\"fingerprint\":{\"alg\":\"sha-256\",\"content\":\"aabbcc\"}}}}");

        assertThat(IDENTITY.of(component).step()).isEqualTo("mat:fingerprint");
        assertThat(IDENTITY.of(component).preImage()).isEqualTo("MAT|password|F|sha-256:aabbcc");
    }

    /**
     * A suite code is refuted only by the protocols that claim it, not by any component carrying a stale block.
     *
     * <p>
     * The refutation walked every component, while the certificate pass beside it gates on the normalized type -- so an
     * algorithm carrying a spurious {@code protocolProperties.cipherSuites} could add a second name for a real code and
     * move the identity of every genuine protocol row claiming it. A component stating <em>no</em> type still
     * contributes, because for a block carrying suites that is more likely a protocol than not, and losing a refutation
     * over-merges.
     */
    @Test
    void onlyAProtocolCanRefuteASuiteCode() {
        String suite = "{\"name\":\"%s\",\"identifiers\":[\"0x1301\"]}";
        JsonNode withStaleBlock = read("{\"components\":[{\"type\":\"cryptographic-asset\",\"name\":\"tls\","
                + "\"cryptoProperties\":{\"assetType\":\"protocol\",\"protocolProperties\":{\"type\":\"tls\","
                + "\"version\":\"1.3\",\"cipherSuites\":[" + suite.formatted("TLS_AES_128_GCM_SHA256")
                + "]}}},{\"type\":\"cryptographic-asset\",\"name\":\"rsa\",\"cryptoProperties\":"
                + "{\"assetType\":\"algorithm\",\"protocolProperties\":{\"cipherSuites\":["
                + suite.formatted("SOMETHING_ELSE") + "]}}}]}");

        assertThat(DocumentScope.of(withStaleBlock, NORMALIZER).refutedSuiteCodes())
                .describedAs("the algorithm's stale block does not refute a real protocol's code")
                .isEmpty();
    }

    /** A producer's spelling routes on the reference whitespace set, not on the narrower JDK one. */
    @Test
    void anAssetTypeSpelledWithANoBreakSpaceStillRoutes() {
        String noBreakSpace = Character.toString(0x00A0);

        assertThat(NORMALIZER.normalizeAssetType("related" + noBreakSpace + "crypto" + noBreakSpace + "material"))
                .isEqualTo(NORMALIZER.normalizeAssetType("related crypto material"))
                .isNotNull();
    }

    /**
     * A refused parameter set is reported as the value the producer wrote.
     *
     * <p>
     * Through a saturating {@code (int)} cast the note read {@code size 2147483647 ... outside whitelist}, naming a
     * number nobody sent. It does <b>not</b> reject {@code 64.0000000000000000001}: measured on this project's mapper
     * that literal arrives as {@code DoubleNode(64.0)}, so its precision is gone before the parse sees it.
     */
    @Test
    void aRefusedParameterSetNamesWhatTheProducerWrote() {
        NormalizedAsset asset = normalize(algorithmComponent("RSA", "{\"parameterSetIdentifier\": 9007199254740993}"));

        assertThat(asset.parameterSet()).isNull();
        assertThat(asset.notes())
                .anySatisfy(note -> assertThat(note).contains("9007199254740993").doesNotContain("2147483647"));
    }

    /**
     * A duplicated {@code bom-ref} resolves to nothing, so document order cannot decide a key.
     *
     * <p>
     * Nothing in either schema version makes {@code bom-ref} unique and real producer output duplicates it.
     * First-in-document-order made a certificate's key depend on which serialization of one document was ingested,
     * against the permutation guarantee the extractor states.
     */
    @Test
    void aDuplicatedRefResolvesToNothingRatherThanToTheFirstDefinition() {
        JsonNode document = read("{\"components\":[{\"type\":\"cryptographic-asset\",\"bom-ref\":\"dup\","
                + "\"name\":\"first\"},{\"type\":\"cryptographic-asset\",\"bom-ref\":\"dup\","
                + "\"name\":\"second\"},{\"type\":\"cryptographic-asset\",\"bom-ref\":\"unique\","
                + "\"name\":\"third\"}]}");
        DocumentScope scope = DocumentScope.of(document, NORMALIZER);

        assertThat(scope.resolve(new TextNode("dup"))).isNull();
        assertThat(scope.resolve(new TextNode("unique"))).isNotNull();
        assertThat(scope.ambiguousRefs()).containsExactly("dup");
    }

    // ---------------------------------------------------------------- grammar and token rules (core#2165)

    /**
     * A digest is not erased because its family's spelling is truncated into the winning family's token.
     *
     * <p>
     * The filter asked whether the winner's token <em>contains</em> the secondary token's first hyphen-part. A token
     * carries its size, so that truncated {@code sha-2-256}'s family to {@code sha}, which {@code sha-3} contains:
     * {@code SHA-256 with SHA3} and {@code SHA3-256 with} both keyed as one, which is the weak-crypto erasure the
     * filter exists to prevent, performed by the filter.
     *
     * <p>
     * Only the truncation is repaired. The containment stays a substring test because vectors {@code gen-218} and
     * {@code gen-219} ratify it -- a component named {@code RSAES-OAEP} keys with an empty variant slot, so the
     * {@code aes} read out of its own spelling is meant to be erased. A name stating both, {@code RSAES-OAEP-AES256},
     * is the undecided case: an adjudication on core#2165 rather than a defect under a ratified vector.
     */
    @Test
    void aDigestIsNotErasedByTruncatingItsFamilyIntoTheWinner() {
        assertThat(NORMALIZER.secondaryTokens("SHA-256 with SHA3", "SHA-3")).contains("sha-2");
        assertThat(keyOfAlgorithm("SHA-256 with SHA3")).isNotEqualTo(keyOfAlgorithm("SHA3-256 with"));
        assertThat(NORMALIZER.secondaryTokens("RSAES-OAEP", "RSAES-OAEP"))
                .describedAs("gen-218 and gen-219 ratify the empty variant slot for this name")
                .doesNotContain("aes");
    }

    /**
     * A grammar token whose own family the winner already spells still folds away.
     *
     * <p>
     * {@code ECDSA} spells {@code DSA}, which says nothing the family slot has not said, and vector
     * {@code alg-curve-fold} pins that pre-image. A secondary <em>marker</em> is a different rule and is deliberately
     * kept: {@code poly1305} beside a ChaCha20-Poly1305 winner separates the AEAD from the bare stream cipher.
     */
    @Test
    void aTokenTheWinnerAlreadySpellsStillFoldsAway() {
        assertThat(NORMALIZER.secondaryTokens("ECDSA", "ECDSA")).doesNotContain("dsa");
        assertThat(NORMALIZER.secondaryTokens("ChaCha20-Poly1305", "ChaCha20-Poly1305"))
                .describedAs("a marker is not a grammar token")
                .isEqualTo("poly1305");
    }

    /**
     * A stateful hash-based signature keeps its own family instead of keying as a digest.
     *
     * <p>
     * Both rules were fully anchored, so every real parameter-set spelling fell through the grammar to the SHA-2 rule
     * and an XMSS or LMS asset was inventoried as a digest family.
     */
    @ParameterizedTest
    @CsvSource({"XMSS-SHA2_10_256,XMSS", "XMSS-MT,XMSS", "XMSS,XMSS", "HSS-LMS-SHA256-M32-H5,LMS", "HSS-LMS,LMS"})
    void aParameterSetSpellingKeepsItsSignatureFamily(String name, String expected) {
        assertThat(normalize(name).family()).isEqualTo(expected);
    }

    /**
     * The whitespace the reference set adds is stripped before a sentinel or a family token is looked up.
     *
     * <p>
     * {@code String.strip} consults {@code Character.isWhitespace}, which does not treat U+00A0 as whitespace, so a
     * value pasted out of a document escaped the sentinel guard and grew a permanent bogus bucket beside the real one.
     */
    @Test
    void aNoBreakSpaceDoesNotEscapeTheSentinelOrTokenLookup() {
        String noBreakSpace = Character.toString(0x00A0);

        assertThat(TABLES.isSentinel("0.0.0.0" + noBreakSpace)).isTrue();
        assertThat(TABLES.isSentinel("unknown" + noBreakSpace)).isTrue();
        assertThat(TABLES.familyToken("RSA" + noBreakSpace)).isEqualTo("RSA");
    }

    private static JsonNode certificateComponent(String notValidBefore, String notValidAfter) {
        return read("{\"type\":\"cryptographic-asset\",\"name\":\"cert\",\"cryptoProperties\":{\"assetType\":"
                + "\"certificate\",\"certificateProperties\":{\"subjectName\":\"CN=one\",\"notValidBefore\":"
                + quote(notValidBefore) + ",\"notValidAfter\":" + quote(notValidAfter) + "}}}");
    }

    /** A protocol row with no version and unreadable suites, which is what the occurrence tier answers. */
    private static JsonNode protocolAt(String location) {
        return read("{\"type\":\"cryptographic-asset\",\"evidence\":{\"occurrences\":[{\"location\":" + quote(location)
                + "}]},\"cryptoProperties\":{\"assetType\":\"protocol\","
                + "\"protocolProperties\":{\"type\":\"tls\",\"cipherSuites\":[{}]}}}");
    }

    /** A certificate carrying both names and both validities, which is what reaches the composite tier. */
    private static JsonNode dnCompositeComponent(String notValidBefore, String notValidAfter) {
        return read("{\"type\":\"cryptographic-asset\",\"name\":\"cert\",\"cryptoProperties\":{\"assetType\":"
                + "\"certificate\",\"certificateProperties\":{\"subjectName\":\"CN=one\",\"issuerName\":"
                + "\"CN=ca\",\"notValidBefore\":" + quote(notValidBefore) + ",\"notValidAfter\":" + quote(notValidAfter)
                + "}}}");
    }

    private static JsonNode materialWithFingerprint(String algorithm, String content) {
        return read("{\"type\":\"cryptographic-asset\",\"cryptoProperties\":{\"assetType\":"
                + "\"relatedCryptoMaterial\",\"relatedCryptoMaterialProperties\":{\"type\":\"public-key\","
                + "\"fingerprint\":{\"alg\":" + quote(algorithm) + ",\"content\":" + quote(content) + "}}}}");
    }

    /**
     * A versioned, nameless protocol row whose declared suites cannot be read, which is what reaches the occurrence
     * tier: the version tier above it fires only for a row that offered no suites at all.
     */
    private static JsonNode protocolAtOneLocation(String version) {
        return read("{\"type\":\"cryptographic-asset\",\"evidence\":{\"occurrences\":[{\"location\":"
                + "\"host:443\"}]},\"cryptoProperties\":{\"assetType\":\"protocol\",\"protocolProperties\":"
                + "{\"type\":\"tls\",\"version\":" + quote(version) + ",\"cipherSuites\":[{}]}}}");
    }

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
