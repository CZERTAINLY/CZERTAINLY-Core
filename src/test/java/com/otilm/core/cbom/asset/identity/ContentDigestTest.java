package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The content-derived slots: a public key's digest, a certificate's claimed digests, and a cipher suite's code.
 *
 * <p>
 * These get their own suite because they are the arbitrary steps. An arbitrary construction that is not written down is
 * unguessable -- recovering the public-key digest cost one proof-of-concept round roughly 16 000 excluded candidate
 * transforms, and 295 of its 296 remaining certificate divergences sat in that one function afterwards, because the
 * rule shipped mis-described by its own prose.
 */
class ContentDigestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ---------------------------------------------------------------- public-key material

    /**
     * Decodable base64 hashes the <b>lowercase hex rendering</b> of the decoded bytes, not the bytes themselves.
     *
     * <p>
     * This is the step that must never be "simplified". {@code sha256(decoded)} reads like the natural definition and
     * would silently re-key every certificate whose public-key target carries a value, so the wrong reading is pinned
     * here beside the right one.
     */
    @Test
    void base64HashesTheHexRenderingAndNotTheBytes() {
        byte[] decoded = {0x00, 0x00, 0x00};

        assertThat(MaterialValueDigest.of("AAAA"))
                .isEqualTo(Digests.sha256Hex(HexFormat.of().formatHex(decoded)))
                .isNotEqualTo(Digests.sha256HexOfBytes(decoded))
                .isNotEqualTo(Digests.sha256Hex("AAAA"));
    }

    /**
     * Anything that is not decodable base64 hashes verbatim, whitespace and PEM armour included.
     *
     * <p>
     * This branch fires for the commonest real spelling, which is why it cannot be an afterthought.
     */
    @ParameterizedTest
    @ValueSource(
            strings = {"-----BEGIN PUBLIC KEY-----\nMIIB\n-----END PUBLIC KEY-----", "not base64!", "AAA", "AAAAA"})
    void anythingElseHashesVerbatim(String value) {
        assertThat(MaterialValueDigest.of(value)).isEqualTo(Digests.sha256Hex(value));
    }

    /**
     * A value that looks like hex but is also legal base64 takes the base64 branch, because the rule tests the alphabet
     * and the length rather than the author's intent.
     *
     * <p>
     * Recorded as a positive case rather than left implicit: {@code deadbeef} is eight characters, every one of them in
     * the standard alphabet, so it decodes -- and a reader who assumes "hex means verbatim" will write the wrong test
     * and then, worse, the wrong fix. Verified against the reference, which agrees byte for byte.
     */
    @Test
    void aHexLookingValueThatIsAlsoLegalBase64Decodes() {
        assertThat(MaterialValueDigest.of("deadbeef"))
                .isNotEqualTo(Digests.sha256Hex("deadbeef"))
                .isEqualTo(
                        Digests.sha256Hex(HexFormat.of().formatHex(java.util.Base64.getDecoder().decode("deadbeef"))));
    }

    /**
     * The alphabet and length tests are stated rather than delegated to a decoder.
     *
     * <p>
     * The reference originally delegated them to a lenient decoder that discards non-alphabet characters and then
     * rejects on the leftover length. Java's MIME decoder is lenient exactly where that one is strict, so a delegated
     * implementation would have been <em>more permissive than the specification while passing every vector</em> --
     * surfacing years later as two rows for one key.
     */
    @Test
    void aValueJavaWouldDecodeLenientlyIsNotTreatedAsBase64() {
        assertThat(MaterialValueDigest.of("zz!!"))
                .describedAs("Java's MIME decoder accepts this; the specification does not")
                .isEqualTo(Digests.sha256Hex("zz!!"));
    }

    /** ASCII whitespace is removed before the alphabet test, so a wrapped value still decodes. */
    @Test
    void wrappedBase64StillDecodes() {
        assertThat(MaterialValueDigest.of("AA\nAA")).isEqualTo(MaterialValueDigest.of("AAAA"));
    }

    @Test
    void anEmptyValueHashesVerbatim() {
        assertThat(MaterialValueDigest.of("")).isEqualTo(Digests.sha256Hex(""));
    }

    // ---------------------------------------------------------------- certificate digests

    /** Strongest first, so two producers listing the same hashes in different orders agree. */
    @Test
    void theStrongestAvailableHashWins() {
        JsonNode component = read(
                "{\"hashes\":[{\"alg\":\"SHA-1\",\"content\":\"aa\"}," + "{\"alg\":\"SHA-256\",\"content\":\"bb\"}]}");

        assertThat(CertificateDigests.componentHash(component)).isEqualTo("sha-256:bb");
    }

    /** A weak digest still identifies, so it is accepted rather than dropped -- refusing it loses a row. */
    @Test
    void aWeakDigestIsStillAnIdentity() {
        assertThat(CertificateDigests.componentHash(read("{\"hashes\":[{\"alg\":\"SHA-1\",\"content\":\"aa\"}]}")))
                .isEqualTo("sha-1:aa");
    }

    @Test
    void aDigestAlgorithmNobodyKnowsYieldsNothing() {
        assertThat(CertificateDigests.componentHash(read("{\"hashes\":[{\"alg\":\"MD4\",\"content\":\"aa\"}]}")))
                .isNull();
        assertThat(CertificateDigests.componentHash(read("{}"))).isNull();
        assertThat(CertificateDigests.componentHash(read("{\"hashes\":[]}"))).isNull();
    }

    /** Content and algorithm both fold, so a producer's capitalization cannot split a certificate. */
    @Test
    void digestSpellingDoesNotSplitACertificate() {
        assertThat(CertificateDigests.componentHash(read("{\"hashes\":[{\"alg\":\"sha-256\",\"content\":\"AABB\"}]}")))
                .isEqualTo("sha-256:aabb");
    }

    /**
     * The 1.7 fingerprint field and an identical component hash produce the same tier, in a fixed order.
     *
     * <p>
     * Tagging them differently would fork one certificate between a 1.6 and a 1.7 producer on the strength of where the
     * same bytes were written.
     */
    @Test
    void bothDigestChannelsAreClaimedFingerprintFirst() {
        JsonNode component = read("{\"hashes\":[{\"alg\":\"SHA-256\",\"content\":\"bb\"}]}");
        JsonNode certificate = read("{\"fingerprint\":{\"alg\":\"SHA-256\",\"content\":\"aa\"}}");

        assertThat(CertificateDigests.claimed(component, certificate)).containsExactly("sha-256:aa", "sha-256:bb");
    }

    /** A fingerprint with no algorithm named defaults to SHA-256, which is what the field means in practice. */
    @Test
    void aFingerprintWithNoAlgorithmDefaults() {
        assertThat(CertificateDigests.claimed(read("{}"), read("{\"fingerprint\":{\"content\":\"aa\"}}")))
                .containsExactly("sha-256:aa");
    }

    @Test
    void aCertificateClaimingNothingYieldsNoDigests() {
        assertThat(CertificateDigests.claimed(read("{}"), read("{}"))).isEmpty();
        assertThat(CertificateDigests.claimed(read("{}"), null)).isEmpty();
    }

    // ---------------------------------------------------------------- cipher suites

    /**
     * Three encodings occur in real documents and all three must land on the same IANA code.
     */
    @ParameterizedTest
    @CsvSource({
            "'[\"0x13\",\"0x1\"]', 1301",
            "'[\"0x13\",\"0x01\"]', 1301",
            "'[\"0x13,0x01\"]', 1301",
            "'[\"0xC0\",\"0x30\"]', c030"})
    void everyEncodingOfOneSuiteYieldsOneCode(String identifiers, String expected) {
        assertThat(CipherSuites.code(read(identifiers))).isEqualTo(expected);
    }

    /**
     * Bytes join within a suite in array order, before any sorting.
     *
     * <p>
     * Flattening across suites collides: these two suite sets share a byte multiset and would otherwise hash alike.
     */
    @Test
    void codesJoinInArrayOrderSoSuiteSetsCannotCollide() {
        assertThat(CipherSuites.code(read("[\"0xC0\",\"0x2B\"]"))).isEqualTo("c02b");
        assertThat(CipherSuites.code(read("[\"0x2B\",\"0xC0\"]"))).isEqualTo("2bc0");
    }

    /** A list this implementation cannot read yields no code at all, rather than a partial one. */
    @Test
    void anUnreadableIdentifierListYieldsNoCode() {
        assertThat(CipherSuites.code(read("[\"nonsense\"]"))).isNull();
        assertThat(CipherSuites.code(read("[]"))).isNull();
        assertThat(CipherSuites.code(read("\"not-an-array\""))).isNull();
        assertThat(CipherSuites.code(null)).isNull();
    }

    /**
     * A non-textual element makes the whole list unreadable, rather than being passed over.
     *
     * <p>
     * Skipping it let a malformed list impersonate a well-formed one: the code below is byte-identical to the code of
     * {@code ["0x13", "0x01"]}, so a suite nobody declared would have been hashed into a protocol identity.
     */
    @Test
    void aNonTextualIdentifierMakesTheListUnreadable() {
        assertThat(CipherSuites.code(read("[\"0x13\",{},\"0x01\"]"))).isNull();
        assertThat(CipherSuites.code(read("[\"0x13\",13,\"0x01\"]"))).isNull();
        assertThat(CipherSuites.code(read("[\"0x13\",\"0x01\"]"))).isEqualTo("1301");
    }

    /**
     * An unpaired surrogate is refused rather than digested.
     *
     * <p>
     * {@code String.getBytes(UTF_8)} substitutes {@code ?} for one silently, which is an identity collision: three
     * distinct producer strings would share one pre-image.
     */
    @Test
    void anUnpairedSurrogateCannotBeDigested() {
        assertThatThrownBy(() -> Digests.sha256Hex("RSA\uD800")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Digests.sha256Hex("\uDC00RSA")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Digests.sha256Hex("RSA?")).isNotEqualTo(Digests.sha256Hex("RSA"));
        assertThat(Digests.sha256Hex("RSA\uD83D\uDE00")).isNotBlank();
    }

    /**
     * A suite whose code the document refuted falls back to its name rather than vanishing.
     *
     * <p>
     * One producer stamps one placeholder code on three differently-named suites, which would otherwise collapse five
     * distinct suites onto one identity.
     */
    @Test
    void aRefutedCodeFallsBackToTheSuiteName() {
        JsonNode properties = read("{\"protocolProperties\":{\"cipherSuites\":["
                + "{\"name\":\"TLS_AES_128_GCM_SHA256\",\"identifiers\":[\"0xC0\",\"0x30\"]}]}}");

        assertThat(CipherSuites.tokens(properties, Set.of())).isEqualTo("c030");
        assertThat(CipherSuites.tokens(properties, Set.of("c030"))).isEqualTo("TLS_AES_128_GCM_SHA256");
    }

    /** A name-only suite list is still resolvable, which is what makes a name-emitting producer split visibly. */
    @Test
    void aNameOnlySuiteListStillResolves() {
        JsonNode properties = read(
                "{\"protocolProperties\":{\"cipherSuites\":[{\"name\":\"tls_aes_128_gcm_sha256\"}]}}");

        assertThat(CipherSuites.tokens(properties, Set.of())).isEqualTo("TLS_AES_128_GCM_SHA256");
        assertThat(CipherSuites.declared(properties)).isTrue();
    }

    /** "Declared but unreadable" must never look like "none were offered". */
    @Test
    void declaredButUnreadableIsNotTheSameAsAbsent() {
        JsonNode declared = read("{\"protocolProperties\":{\"cipherSuites\":[{}]}}");
        JsonNode absent = read("{\"protocolProperties\":{}}");

        assertThat(CipherSuites.tokens(declared, Set.of())).isNull();
        assertThat(CipherSuites.declared(declared)).isTrue();
        assertThat(CipherSuites.declared(absent)).isFalse();
    }

    private static JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("test fixture is not JSON: " + json, e);
        }
    }
}
