package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The redaction proof: no material value survives to anything that stores, keys, logs or serves it.
 *
 * <p>
 * The property under test is an ordering one. Redaction runs before identity, persistence and logging can observe the
 * payload, so these assertions check the <em>output payload</em> for the plaintext rather than checking that some
 * caller remembered to redact.
 */
class MaterialRedactionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SECRET = "s3cr3t-key-material";

    @Test
    void thePlaintextNeverReachesTheStoredPayload() {
        MaterialRedaction redaction = redact("public-key", SECRET);

        assertThat(CanonicalJson.canonicalize(redaction.payload())).doesNotContain(SECRET);
        assertThat(redaction.payload().toString()).doesNotContain(SECRET);
    }

    @Test
    void theCallersInputIsNotMutated() {
        JsonNode properties = read(
                "{\"relatedCryptoMaterialProperties\":{\"type\":\"public-key\",\"value\":\"" + SECRET + "\"}}");

        MaterialRedaction.of(properties);

        assertThat(properties.at("/relatedCryptoMaterialProperties/value").asText())
                .describedAs("redaction returns a copy; a caller holding the original must be unaffected")
                .isEqualTo(SECRET);
    }

    /**
     * The published digest is withheld for low-entropy material, and the identity digest is not.
     *
     * <p>
     * An unsalted SHA-256 of a password or a token is rainbow-table reversible, so publishing it in a payload that is
     * served back is the same leak one step removed. The identity digest still exists, because the identity key is a
     * hash of a whole pre-image and is never exposed on any API -- it is what keeps two different passwords at one
     * source coordinate apart.
     */
    @ParameterizedTest
    @ValueSource(strings = {"password", "token", "credential", "PASSWORD"})
    void lowEntropyMaterialPublishesNoDigestButStillKeysApart(String type) {
        MaterialRedaction redaction = redact(type, "hunter2");

        assertThat(redaction.publishedDigest()).isNull();
        assertThat(redaction.identityDigest()).isNotNull().hasSize(64);
        assertThat(redaction.payload().at("/relatedCryptoMaterialProperties/value/sha256").isMissingNode()).isTrue();
        assertThat(redaction.payload().at("/relatedCryptoMaterialProperties/value/length").asInt()).isEqualTo(7);
        assertThat(redaction.findings()).anySatisfy(finding -> assertThat(finding).contains("digest withheld"));
    }

    @Test
    void twoDifferentPasswordsAtOneCoordinateStillKeyApart() {
        assertThat(redact("password", "one").identityDigest()).isNotEqualTo(redact("password", "two").identityDigest());
    }

    @Test
    void publishableMaterialCarriesItsDigestAndLength() {
        MaterialRedaction redaction = redact("public-key", "QUJDRA==");

        assertThat(redaction.publishedDigest()).isEqualTo(redaction.identityDigest());
        assertThat(redaction.payload().at("/relatedCryptoMaterialProperties/value/sha256").asText())
                .isEqualTo(redaction.publishedDigest());
        assertThat(redaction.payload().at("/relatedCryptoMaterialProperties/value/$redacted").asText())
                .isEqualTo(MaterialRedaction.REDACTED_MARKER);
    }

    /** Fails closed: guessing wrong on an unknown type would publish a reversible digest. */
    @Test
    void anAbsentMaterialTypeIsTreatedAsLowEntropy() {
        JsonNode properties = read("{\"relatedCryptoMaterialProperties\":{\"value\":\"whatever\"}}");

        MaterialRedaction redaction = MaterialRedaction.of(properties);

        assertThat(redaction.publishedDigest()).isNull();
        assertThat(redaction.identityDigest()).isNotNull();
    }

    /**
     * A producer inlining a value on a type that should never carry one has exfiltrated key material into a document
     * the platform then aggregates estate-wide. It is raised, not silently cleaned up.
     */
    @ParameterizedTest
    @ValueSource(strings = {"private-key", "secret-key", "shared-secret", "seed", "key"})
    void anInlinedSecretIsReportedAsAnIngestFinding(String type) {
        assertThat(redact(type, SECRET).findings())
                .anySatisfy(finding -> assertThat(finding).contains("producer inlined a value"));
    }

    @Test
    void aNonStringValueIsDroppedRatherThanKept() {
        JsonNode properties = read("{\"relatedCryptoMaterialProperties\":{\"type\":\"secret-key\",\"value\":123}}");

        MaterialRedaction redaction = MaterialRedaction.of(properties);

        assertThat(redaction.payload().at("/relatedCryptoMaterialProperties/value").isMissingNode()).isTrue();
        assertThat(redaction.identityDigest()).isNull();
        assertThat(redaction.findings()).contains("non-string material value dropped");
    }

    /**
     * Length counts code points, because the reference counts characters and the length is served back in the payload.
     * A UTF-16 count would report 2 for one astral character.
     */
    @Test
    void lengthCountsCodePointsNotUtf16Units() {
        assertThat(redact("public-key", "😀").valueLength()).isEqualTo(1);
    }

    @Test
    void theValueIsHashedVerbatimWithNoDecodeOrTrim() {
        assertThat(redact("public-key", " AAAA ").identityDigest())
                .describedAs("normalizing first would make identity depend on the normalizer")
                .isEqualTo(Digests.sha256Hex(" AAAA "));
    }

    @Test
    void propertiesWithNoMaterialBlockPassThroughUntouched() {
        JsonNode properties = read("{\"assetType\":\"algorithm\",\"algorithmProperties\":{\"primitive\":\"hash\"}}");

        MaterialRedaction redaction = MaterialRedaction.of(properties);

        assertThat(CanonicalJson.canonicalize(redaction.payload())).isEqualTo(CanonicalJson.canonicalize(properties));
        assertThat(redaction.findings()).isEmpty();
    }

    @Test
    void anAbsentPropertiesBlockYieldsAnEmptyPayloadRatherThanFailing() {
        MaterialRedaction redaction = MaterialRedaction.of(null);

        assertThat(CanonicalJson.canonicalize(redaction.payload())).isEqualTo("{}");
        assertThat(redaction.identityDigest()).isNull();
    }

    private static MaterialRedaction redact(String type, String value) {
        return MaterialRedaction
                .of(read("{\"relatedCryptoMaterialProperties\":{\"type\":\"" + type + "\",\"value\":" + quote(value)
                        + "}}"));
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
