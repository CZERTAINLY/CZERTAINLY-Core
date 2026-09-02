package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.otilm.core.model.cbom.CryptoAssetIdentityGuard;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The document-scoped derivations: what refutes a producer-supplied identifier, what does not, and what a refutation
 * does to the key.
 *
 * <p>
 * Every ratified vector wraps one component in a document of its own, so the vector suite can never watch two
 * components contradict each other, and the extractor fixtures carried no shared digest or suite code either. Until
 * this class existed, reverting {@link DocumentScope}'s refutation to whole-tuple comparison -- the regression its own
 * Javadoc records -- or making {@link DocumentScope#refutedAcross} return nothing left every test green. Each test here
 * names the mutation it fails under.
 */
class DocumentScopeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final AssetNormalizer NORMALIZER = new AssetNormalizer(IdentityTables.load());

    private static final CryptoAssetIdentity IDENTITY = new CryptoAssetIdentity(NORMALIZER);

    private static final String DIGEST = "ab".repeat(32);

    /** The spelling under which {@link CertificateDigests} files a SHA-256 {@code component.hashes[]} claim. */
    private static final String CLAIM = "sha-256:" + DIGEST;

    /** Serial and validity: the facts a sparse record leaves unstated. */
    private static final String RICHER_FACTS = ",\"serialNumber\":\"0A\",\"notValidBefore\":\"2026-01-01T00:00:00Z\","
            + "\"notValidAfter\":\"2027-01-01T00:00:00Z\"";

    // ---------------------------------------------------------------- certificate digests

    /**
     * A digest two certificates disagree about is unusable for both. The second certificate sits in a nested component,
     * so a contradiction buried in a sub-tree refutes too.
     */
    @Test
    void twoCertificatesContradictingEachOtherAboutOneDigestRefuteIt() {
        JsonNode document = document(certificate("alpha", "CN=alpha.example", ""),
                library(certificate("beta", "CN=beta.example", "")));

        assertThat(DocumentScope.of(document, NORMALIZER).refutedCertificateDigests()).containsExactly(CLAIM);
    }

    /**
     * Refutation needs a contradiction on a field both records state.
     *
     * <p>
     * Comparing whole tuples made a sparse record conflict with a richer one describing the same certificate, and
     * destroyed a legitimate merge. Under that mutation the two tuples here differ and this assertion fails; the
     * certificates themselves agree on everything both of them say.
     */
    @Test
    void aSparseRecordDoesNotRefuteARicherOneDescribingTheSameCertificate() {
        JsonNode document = document(certificate("sparse", "CN=alpha.example", ""),
                certificate("rich", "CN=alpha.example", RICHER_FACTS));

        assertThat(DocumentScope.of(document, NORMALIZER).refutedCertificateDigests()).isEmpty();
    }

    /**
     * The refutation reaches the key: the digest tier is skipped, the composite answers instead, and the row records
     * why it stayed separate -- which is what the alias-repair refusal reads.
     */
    @Test
    void aRefutedDigestKeysBelowTheDigestTierAndStampsTheGuard() {
        JsonNode alpha = certificate("alpha", "CN=alpha.example", "");
        DocumentScope scope = DocumentScope.of(document(alpha, certificate("beta", "CN=beta.example", "")), NORMALIZER);

        CryptoAssetIdentity.Identity alone = IDENTITY.of(alpha);
        CryptoAssetIdentity.Identity refuted = IDENTITY.of(alpha, scope, Set.of());

        assertThat(alone.step()).isEqualTo("crt:component-hash");
        assertThat(alone.guard()).isNull();
        assertThat(refuted.step()).isEqualTo("crt:dn-composite");
        assertThat(refuted.guard()).isEqualTo(CryptoAssetIdentityGuard.REFUTED_CERTIFICATE_DIGEST);
        assertThat(refuted.key()).isNotEqualTo(alone.key());
    }

    /** A digest a batch-scoped index refuted is honoured exactly as one the document refuted, key and guard alike. */
    @Test
    void aBatchRefutationKeysExactlyAsADocumentRefutationDoes() {
        JsonNode alpha = certificate("alpha", "CN=alpha.example", "");
        DocumentScope scope = DocumentScope.of(document(alpha, certificate("beta", "CN=beta.example", "")), NORMALIZER);

        CryptoAssetIdentity.Identity byDocument = IDENTITY.of(alpha, scope, Set.of());
        CryptoAssetIdentity.Identity byBatch = IDENTITY.of(alpha, DocumentScope.none(), Set.of(CLAIM));

        assertThat(byBatch.guard()).isEqualTo(CryptoAssetIdentityGuard.REFUTED_CERTIFICATE_DIGEST);
        assertThat(byBatch.key()).isEqualTo(byDocument.key());
    }

    /**
     * The hazard no single document can see: two certificates claiming one digest in different documents. Each scope
     * alone refutes nothing; the batch view refutes the digest. A {@code refutedAcross} returning an empty set fails
     * here.
     */
    @Test
    void refutedAcrossSeesAContradictionThatNoSingleDocumentCan() {
        JsonNode first = document(certificate("alpha", "CN=alpha.example", ""));
        JsonNode second = document(certificate("beta", "CN=beta.example", ""));

        assertThat(DocumentScope.of(first, NORMALIZER).refutedCertificateDigests()).isEmpty();
        assertThat(DocumentScope.of(second, NORMALIZER).refutedCertificateDigests()).isEmpty();
        assertThat(DocumentScope.refutedAcross(List.of(first, second), NORMALIZER)).containsExactly(CLAIM);
    }

    /** The batch rule is the document rule over more documents: sparse against rich is still no contradiction. */
    @Test
    void refutedAcrossNeedsTheContradictionTheDocumentRuleNeeds() {
        JsonNode sparse = document(certificate("sparse", "CN=alpha.example", ""));
        JsonNode rich = document(certificate("rich", "CN=alpha.example", RICHER_FACTS));

        assertThat(DocumentScope.refutedAcross(List.of(sparse, rich), NORMALIZER)).isEmpty();
    }

    // ---------------------------------------------------------------- suite codes

    /** A code under two names is refuted; one name spelled in two cases is one name. */
    @Test
    void oneCodeUnderTwoSuiteNamesIsRefutedWhileOneNameRepeatedIsNot() {
        JsonNode contradicted = document(protocol("one", "TLS_AES_128_GCM_SHA256", "\"0x13\",\"0x01\""),
                protocol("two", "TLS_AKE_WITH_AES_128_GCM_SHA256", "\"0x1301\""));
        JsonNode repeated = document(protocol("one", "TLS_AES_128_GCM_SHA256", "\"0x13\",\"0x01\""),
                protocol("two", "tls_aes_128_gcm_sha256", "\"0x1301\""));

        assertThat(DocumentScope.of(contradicted, NORMALIZER).refutedSuiteCodes()).containsExactly("1301");
        assertThat(DocumentScope.of(repeated, NORMALIZER).refutedSuiteCodes()).isEmpty();
    }

    /**
     * A refuted code falls back to the suite's name, so two protocols that would have merged on a placeholder code
     * split on what they actually call the suite.
     */
    @Test
    void aRefutedSuiteCodeFallsBackToTheSuiteNameInTheKey() {
        JsonNode one = protocol("one", "TLS_AES_128_GCM_SHA256", "\"0x13\",\"0x01\"");
        JsonNode two = protocol("two", "TLS_AKE_WITH_AES_128_GCM_SHA256", "\"0x1301\"");
        DocumentScope scope = DocumentScope.of(document(one, two), NORMALIZER);

        assertThat(CipherSuites.tokens(one.get("cryptoProperties"), scope.refutedSuiteCodes()))
                .isEqualTo("n:" + PreImageSlot.of("TLS_AES_128_GCM_SHA256"));
        assertThat(IDENTITY.of(one, scope, Set.of()).key()).isNotEqualTo(IDENTITY.of(two, scope, Set.of()).key());
        assertThat(IDENTITY.of(one).key())
                .describedAs("with no document around them, both sit on the code and merge")
                .isEqualTo(IDENTITY.of(two).key());
    }

    // ---------------------------------------------------------------- helpers

    /** A certificate claiming {@link #DIGEST} through {@code component.hashes[]}, issued by one CA. */
    private static JsonNode certificate(String name, String subject, String furtherFacts) {
        return read("{\"type\":\"cryptographic-asset\",\"name\":\"" + name + "\",\"hashes\":[{\"alg\":\"SHA-256\","
                + "\"content\":\"" + DIGEST + "\"}],\"cryptoProperties\":{\"assetType\":\"certificate\","
                + "\"certificateProperties\":{\"subjectName\":\"" + subject + "\",\"issuerName\":\"CN=vector ca\""
                + furtherFacts + "}}}");
    }

    /** A TLS 1.3 protocol offering one suite, so its key differs from a sibling's only through that suite's token. */
    private static JsonNode protocol(String name, String suiteName, String identifiers) {
        return read("{\"type\":\"cryptographic-asset\",\"name\":\"" + name + "\",\"cryptoProperties\":"
                + "{\"assetType\":\"protocol\",\"protocolProperties\":{\"type\":\"tls\",\"version\":\"1.3\","
                + "\"cipherSuites\":[{\"name\":\"" + suiteName + "\",\"identifiers\":[" + identifiers + "]}]}}}");
    }

    private static JsonNode library(JsonNode... children) {
        ObjectNode library = MAPPER.createObjectNode();
        library.put("type", "library");
        library.put("name", "outer");
        library.set("components", components(children));
        return library;
    }

    private static JsonNode document(JsonNode... components) {
        ObjectNode document = MAPPER.createObjectNode();
        document.set("components", components(components));
        return document;
    }

    private static ArrayNode components(JsonNode... components) {
        ArrayNode array = MAPPER.createArrayNode();
        for (JsonNode component : components) {
            array.add(component);
        }
        return array;
    }

    private static JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("test fixture is not JSON", e);
        }
    }
}
