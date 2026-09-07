package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The chain's own record and the one tier whose detector input is not its pre-image.
 *
 * <p>
 * The tier rules themselves are pinned in {@code NormalizationRulesTest} and the ratified vectors; what lives here is
 * the pair of properties on the {@code mat:fingerprint} tier that a single change traded against each other -- no
 * withheld character reaches a served note, and the case-fold twin detector still sees the row -- each pinned on its
 * own so a future change cannot trade them again, and the printed form of {@link CryptoAssetIdentity.Identity}.
 */
class CryptoAssetIdentityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A schema-shaped SHA-256 fingerprint content: 64 hexadecimal characters, which storage serves verbatim. */
    private static final String HEX = "deadbeef".repeat(8);

    private static final CryptoAssetIdentity IDENTITY = new CryptoAssetIdentity(
            new AssetNormalizer(IdentityTables.load()));

    /**
     * The leak half: a fingerprint claim storage withholds is withheld from the case-risk detector too.
     *
     * <p>
     * {@code mat:fingerprint} renders {@code alg:content} literally and is open to a {@code password} row, so a
     * producer putting non-hex cleartext there put the password in the pre-image -- and the detector, reading the
     * pre-image, published its cased characters in a served R12 note. Storage drops the fingerprint on such a row,
     * which is asserted first because it is the premise: what the row does not serve, the note may not spell. The
     * detector's input is asserted positively, so the exclusion cannot pass on a blinded detector.
     */
    @Test
    void aWithheldFingerprintClaimIsWithheldFromTheDetectorToo() {
        CryptoAssetIdentity.Identity password = IDENTITY.of(materialOfType("password", "sha-256", "Pässwörd-ß"));

        assertThat(password.step()).isEqualTo("mat:fingerprint");
        assertThat(storedMaterial(password).has("fingerprint"))
                .describedAs("storage withholds the fingerprint of a password, which is why the detector must too")
                .isFalse();
        assertThat(password.asset().asciiCaseRisk()).isEmpty();
        assertThat(password.asset().notes()).noneMatch(note -> note.startsWith("R12:"));
        assertThat(password.asset().keyedCaseValues())
                .describedAs("the detector reads the type slot and the algorithm label, and not the content")
                .containsExactly("password", "sha-256");
    }

    /**
     * The detector half: the fingerprint tier is still examined wherever reading it discloses nothing.
     *
     * <p>
     * Closing the leak by reading the type slot alone blinded R12 on the whole tier, so a case-fold twin in the
     * algorithm label -- producer metadata, never the material -- keyed apart with nothing recorded. Each input the
     * detector may read is asserted to fire on its own, and the served row is shown to be served, so the line between
     * this test and the one above is the stored payload and not a type list.
     *
     * <p>
     * The content slot cannot be shown firing on a case-fold twin, and that is a property rather than a gap: storage
     * now serves a {@code fingerprint} only through the hash-content shape, so a served content is hexadecimal and no
     * hexadecimal string carries a non-ASCII cased character. The last two assertions pin that composition from the
     * other side -- a content outside the shape is withheld even on a high-entropy type, and the note stays silent.
     */
    @Test
    void theDetectorStillSeesTheFingerprintTierWhereReadingItDisclosesNothing() {
        NormalizedAsset typeRisk = IDENTITY.of(materialOfType("pässword", "sha-256", HEX)).asset();
        NormalizedAsset labelRisk = IDENTITY.of(materialOfType("password", "sha-Ä256", HEX)).asset();
        CryptoAssetIdentity.Identity served = IDENTITY.of(materialOfType("private-key", "sha-256", HEX));
        CryptoAssetIdentity.Identity withheld = IDENTITY.of(materialOfType("private-key", "sha-256", "deadbeÉf"));

        assertThat(typeRisk.asciiCaseRisk()).describedAs("the type slot is examined").containsExactly("ä");
        assertThat(labelRisk.asciiCaseRisk()).describedAs("so is the algorithm label").containsExactly("Ä");
        assertThat(labelRisk.notes()).anyMatch(note -> note.startsWith("R12:"));
        assertThat(storedMaterial(served).get("fingerprint").get("content").textValue())
                .describedAs("storage serves a schema-shaped fingerprint of a private key verbatim")
                .isEqualTo(HEX);
        assertThat(served.asset().keyedCaseValues())
                .describedAs("on a served row the detector reads the whole pre-image, as on every other tier")
                .containsExactly(served.preImage());
        assertThat(storedMaterial(withheld).has("fingerprint"))
                .describedAs("a content outside the hash-content shape is withheld, however high-entropy the type")
                .isFalse();
        assertThat(withheld.asset().asciiCaseRisk())
                .describedAs("and what storage withholds the note may not spell, on any type")
                .isEmpty();
    }

    /**
     * The hand-written {@code toString} is load-bearing: a record prints every component, and this one carries the
     * stored value and the string it hashed. Deleting the override left every fence test green, because the logging
     * rule judges the words on the calling line and a record printed through {@code {}} carries none of them.
     */
    @Test
    void aPrintedIdentityNamesItsStepAndNeverWhatItHashed() {
        CryptoAssetIdentity.Identity identity = IDENTITY
                .of(read("{\"type\":\"cryptographic-asset\",\"name\":\"RSA-2048\",\"cryptoProperties\":"
                        + "{\"assetType\":\"algorithm\",\"algorithmProperties\":{}}}"));

        assertThat(identity.toString())
                .contains(identity.step())
                .doesNotContain(identity.key())
                .doesNotContain(identity.preImage());
    }

    private static JsonNode storedMaterial(CryptoAssetIdentity.Identity identity) {
        return identity.redaction().storedPayload().get("relatedCryptoMaterialProperties");
    }

    private static JsonNode materialOfType(String type, String fingerprintAlgorithm, String fingerprintContent) {
        return read("{\"type\":\"cryptographic-asset\",\"cryptoProperties\":{\"assetType\":"
                + "\"related-crypto-material\",\"relatedCryptoMaterialProperties\":{\"type\":" + quote(type)
                + ",\"fingerprint\":{\"alg\":" + quote(fingerprintAlgorithm) + ",\"content\":"
                + quote(fingerprintContent) + "}}}}");
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
