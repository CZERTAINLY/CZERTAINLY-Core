package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otilm.core.cbom.asset.OccurrenceEvidenceCapper;
import com.otilm.core.serialization.ObjectMapperFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The walker: what a document yields, and what no document can make it do.
 *
 * <p>
 * A CBOM is untrusted input from a third-party scanner, so most of what is asserted here is about robustness rather
 * than about extraction. The governing property is that <b>nothing a producer can write makes the walk fail</b>: one
 * malformed asset must not cost an operator the other four thousand.
 */
class CbomAssetExtractorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final CbomAssetExtractor EXTRACTOR = new CbomAssetExtractor(
            new CryptoAssetIdentity(new AssetNormalizer(IdentityTables.load())));

    // ---------------------------------------------------------------- extraction

    @Test
    void nestedComponentTreesAreWalkedToTheirLeaves() {
        JsonNode document = read("{\"components\":[{\"type\":\"library\",\"name\":\"outer\",\"components\":["
                + algorithm("AES-256") + ",{\"type\":\"library\",\"name\":\"inner\",\"components\":["
                + algorithm("RSA-2048") + "]}]}]}");

        CbomAssetExtractor.Extraction extraction = EXTRACTOR.extract(document);

        assertThat(extraction.assets()).hasSize(2);
        assertThat(extraction.skips()).isEmpty();
    }

    /**
     * A component typed {@code cryptographic-asset} with no {@code cryptoProperties} is extracted, not skipped.
     *
     * <p>
     * It keys on the unroutable backstop tier with its name. There is one in the validation corpus, and skipping it
     * would lose the only record that a producer emitted something the specification cannot route.
     */
    @Test
    void aComponentWithNoCryptoPropertiesIsUnroutableRatherThanSkipped() {
        CbomAssetExtractor.Extraction extraction = EXTRACTOR
                .extract(read("{\"components\":[{\"type\":\"cryptographic-asset\",\"name\":\"broken\"}]}"));

        assertThat(extraction.skips()).isEmpty();
        assertThat(extraction.assets())
                .singleElement()
                .satisfies(asset -> assertThat(asset.chainStep()).isEqualTo("backstop:unknown-type"));
    }

    @Test
    void aComponentCarryingCryptoPropertiesIsExtractedWhateverItsDeclaredType() {
        CbomAssetExtractor.Extraction extraction = EXTRACTOR
                .extract(read("{\"components\":[{\"type\":\"library\","
                        + "\"name\":\"x\",\"cryptoProperties\":{\"assetType\":\"algorithm\"}}]}"));

        assertThat(extraction.assets()).hasSize(1);
    }

    @Test
    void componentsThatAreNotCryptographicAssetsAreIgnoredRatherThanSkipped() {
        CbomAssetExtractor.Extraction extraction = EXTRACTOR
                .extract(read("{\"components\":[{\"type\":\"library\",\"name\":\"jackson\"}]}"));

        assertThat(extraction.assets()).isEmpty();
        assertThat(extraction.skips()).describedAs("a library is not a skip; it was never a candidate").isEmpty();
    }

    // ---------------------------------------------------------------- determinism

    /**
     * An asset's identity is a function of the asset, so permuting the document cannot change what results.
     *
     * <p>
     * This is the property the whole inventory rests on: two nodes, two releases and two re-ingests of one document
     * must agree, and the merge must not depend on which producer synced first.
     */
    @Test
    void permutingTheDocumentChangesNothingButOrder() {
        List<String> components = new ArrayList<>(List
                .of(algorithm("AES-256"), algorithm("RSA-2048"), algorithm("SHA-256"), certificate("a"),
                        certificate("b")));
        List<String> forward = keysOf(components);

        Collections.reverse(components);
        List<String> reversed = keysOf(components);
        Collections.shuffle(components, new java.util.Random(20260827));
        List<String> shuffled = keysOf(components);

        assertThat(reversed).containsExactlyInAnyOrderElementsOf(forward);
        assertThat(shuffled).containsExactlyInAnyOrderElementsOf(forward);
    }

    @Test
    void reExtractingTheSameDocumentYieldsTheSameKeys() {
        JsonNode document = read("{\"components\":[" + algorithm("AES-256-GCM") + "," + certificate("x") + "]}");

        assertThat(keysOf(EXTRACTOR.extract(document))).isEqualTo(keysOf(EXTRACTOR.extract(document)));
    }

    // ---------------------------------------------------------------- wild input

    /**
     * Every shape here is schema-valid or near enough that a real producer has emitted it, and none may fail the run.
     *
     * <p>
     * The version number is the sharpest: one producer ships {@code specVersion} as the number {@code 999}. Nothing in
     * this walker reads it, which is why that costs nothing -- the 1.6 and 1.7 differences are field renames absorbed
     * by the normalizer reading both spellings.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"specVersion\":999,\"components\":[{\"type\":\"cryptographic-asset\",\"name\":\"a\",\"cryptoProperties\":{\"assetType\":\"algorithm\"}}]}",
            "{\"specVersion\":\"1.6\",\"components\":[{\"type\":\"cryptographic-asset\",\"name\":\"a\",\"cryptoProperties\":{\"assetType\":\"algorithm\",\"algorithmProperties\":{\"nistQuantumSecurityLevel\":\"not-a-number\"}}}]}",
            "{\"components\":[{\"type\":\"cryptographic-asset\",\"name\":\"a\",\"cryptoProperties\":{\"assetType\":\"algorithm\"},\"evidence\":{}}]}",
            "{\"components\":[{\"type\":\"cryptographic-asset\",\"name\":\"a\",\"cryptoProperties\":{\"assetType\":\"algorithm\"},\"nonstandardField\":{\"deeply\":[1,2,3]}}]}",
            "{\"components\":[{\"type\":\"cryptographic-asset\",\"cryptoProperties\":{\"assetType\":\"algorithm\"}}]}",
            "{\"components\":[{\"type\":\"cryptographic-asset\",\"name\":null,\"cryptoProperties\":{\"assetType\":null}}]}",
            "{\"components\":[{\"type\":\"cryptographic-asset\",\"name\":\"a\",\"cryptoProperties\":{\"assetType\":\"protocol\",\"protocolProperties\":{\"type\":\"tls\",\"version\":\"n/a\",\"cipherSuites\":[{}]}}}]}"})
    void wildButLegalInputParsesWithoutFailingTheRun(String json) {
        CbomAssetExtractor.Extraction extraction = EXTRACTOR.extract(read(json));

        assertThat(extraction.skips()).isEmpty();
        assertThat(extraction.assets()).hasSize(1);
        assertThat(extraction.assets().get(0).key()).hasSize(64);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"components\":[]}",
            "{\"components\":\"not-an-array\"}",
            "{\"components\":[null,1,\"two\"]}"})
    void aDocumentWithNothingToExtractYieldsNothing(String json) {
        CbomAssetExtractor.Extraction extraction = EXTRACTOR.extract(read(json));

        assertThat(extraction.assets()).isEmpty();
        assertThat(extraction.skips()).isEmpty();
    }

    @Test
    void anAbsentDocumentIsNotAFailure() {
        assertThat(EXTRACTOR.extract(null).assets()).isEmpty();
        assertThat(EXTRACTOR.extract(read("[]")).assets()).isEmpty();
    }

    /**
     * A document nested deeply enough to be dangerous is refused by the parser, before the walker is reached.
     *
     * <p>
     * This is the bound that actually protects the ingest, and it is not the walker's: Jackson refuses past 1000 levels
     * by default. The walker's own bound is set to match it, so that nothing which <em>parses</em> is ever truncated --
     * an earlier bound of 64 would have silently discarded every component below it in a document the parser had
     * accepted, which is data loss wearing robustness as a disguise.
     */
    @Test
    void aPathologicallyNestedDocumentIsRefusedByTheParser() {
        assertThatThrownBy(() -> read(nestedDocument(1200)))
                .describedAs("the parser is the real bound; the walker never sees such a document")
                .hasRootCauseInstanceOf(StreamConstraintsException.class);
    }

    /**
     * A document nested deeply but legally is walked to the bottom, with nothing dropped.
     *
     * <p>
     * The companion to the case above, and the one that would have caught the bound being set too low: this depth
     * parses, so every asset in it must come out.
     *
     * <p>
     * Note the units differ, which is why the walker's bound can never bite. One component level costs <em>two</em>
     * JSON nesting levels -- the object and its {@code components} array -- so the parser's 1000-level limit admits
     * roughly 499 component levels, against the walker's bound of 1000. The fixture is sized in component levels.
     */
    @Test
    void aDeeplyButLegallyNestedDocumentLosesNothing() {
        CbomAssetExtractor.Extraction extraction = EXTRACTOR.extract(read(nestedDocument(400)));

        assertThat(extraction.assets()).describedAs("the asset at the bottom must still be found").hasSize(1);
        assertThat(extraction.depthLimitReached()).isFalse();
        assertThat(extraction.skips()).isEmpty();
    }

    /**
     * A skip names the component and the failure class, never the payload.
     *
     * <p>
     * A document is untrusted input. A skip that quoted what it could not parse would be a disclosure channel for
     * exactly the documents least worth trusting, and it would sit outside everything the redaction proof covers.
     */
    @Test
    void aSkipNamesTheComponentAndNeverThePayload() {
        String secret = "s3cr3t-material-value";
        JsonNode document = read("{\"components\":[{\"type\":\"cryptographic-asset\",\"name\":\"leaky\","
                + "\"cryptoProperties\":{\"assetType\":\"related-crypto-material\","
                + "\"relatedCryptoMaterialProperties\":{\"type\":\"private-key\",\"value\":\"" + secret + "\"}}}]}");

        CbomAssetExtractor.Extraction extraction = EXTRACTOR.extract(document);

        assertThat(extraction.assets()).hasSize(1);
        assertThat(extraction.toString()).doesNotContain(secret);
        assertThat(extraction.skips()).allSatisfy(skip -> assertThat(skip.reason()).doesNotContain(secret));
    }

    /**
     * One unkeyable component costs its own row and nothing else.
     *
     * <p>
     * This is the class's headline guarantee -- a producer's malformed asset must not cost an operator the other four
     * thousand -- and until this test existed nothing in the suite ever produced a non-empty skip list, so the broad
     * {@code catch (RuntimeException)} it rests on was never executed. A lone surrogate is the cheapest trigger: the
     * identity digest refuses one rather than letting the encoder fold it onto {@code ?}. It goes in the material
     * value, which is the field the digest actually reads -- a surrogate in the component name would sit outside this
     * tier's pre-image and key perfectly well.
     */
    @Test
    void oneUnkeyableComponentIsSkippedAndTheRestSurvive() {
        String secret = "s3cr3t-material-value";
        JsonNode document = read("{\"components\":[" + "{\"type\":\"cryptographic-asset\",\"name\":\"good-one\","
                + "\"cryptoProperties\":{\"assetType\":\"algorithm\",\"algorithmProperties\":"
                + "{\"primitive\":\"block-cipher\"}}}," + "{\"type\":\"cryptographic-asset\",\"name\":\"broken\","
                + "\"cryptoProperties\":{\"assetType\":\"related-crypto-material\","
                + "\"relatedCryptoMaterialProperties\":{\"type\":\"private-key\",\"value\":\"" + secret
                + "\\ud800\"}}}," + "{\"type\":\"cryptographic-asset\",\"name\":\"good-two\","
                + "\"cryptoProperties\":{\"assetType\":\"algorithm\",\"algorithmProperties\":"
                + "{\"primitive\":\"hash\"}}}]}");

        CbomAssetExtractor.Extraction extraction = EXTRACTOR.extract(document);

        assertThat(extraction.skips())
                .describedAs("exactly the malformed component is skipped")
                .singleElement()
                .satisfies(skip -> assertThat(skip.componentName()).isEqualTo("broken"));
        assertThat(extraction.assets())
                .describedAs("the other two are keyed regardless")
                .extracting(CbomAssetExtractor.ExtractedAsset::componentName)
                .containsExactly("good-one", "good-two");
        assertThat(extraction.skips().getFirst().reason())
                .describedAs("the failure class only -- never the payload, and never the exception's own message")
                .isEqualTo("IllegalArgumentException")
                .doesNotContain(secret);
    }

    /**
     * A location long enough to be capped must not be cut through a surrogate pair.
     *
     * <p>
     * The cap counts UTF-16 units, so a path ending in an astral character used to leave a lone high surrogate behind
     * -- well-formed producer input made malformed by the platform, which the identity digest then refused, so the
     * asset vanished with nothing an operator could act on.
     */
    @Test
    void anAstralCharacterAtTheLocationCapDoesNotCostTheAsset() {
        String location = "file:///" + "a".repeat(1_020) + "\uD83D\uDD11/key.pem";
        JsonNode document = read("{\"components\":[{\"type\":\"cryptographic-asset\",\"name\":\"TLSv1.2\","
                + "\"cryptoProperties\":{\"assetType\":\"protocol\",\"protocolProperties\":{\"type\":\"tls\"}},"
                + "\"evidence\":{\"occurrences\":[{\"location\":\"" + location + "\"}]}}]}");

        CbomAssetExtractor.Extraction extraction = EXTRACTOR.extract(document);

        assertThat(extraction.skips()).isEmpty();
        assertThat(extraction.assets()).hasSize(1);
    }

    // ---------------------------------------------------------------- the ingest mapper

    /**
     * The ingest mapper must not carry source text into a parse-failure message.
     *
     * <p>
     * With {@code INCLUDE_SOURCE_IN_LOCATION} enabled, a {@link com.fasterxml.jackson.core.JsonParseException} quotes
     * hundreds of characters of the document around the failure. A single {@code log.warn(message, exception)} on a
     * malformed document then prints raw, <em>pre-redaction</em> content into an appender -- key material included --
     * which is a channel the redaction proof does not cover, because redaction never ran.
     *
     * <p>
     * Jackson disables it by default. This asserts it rather than relying on that, because the default is a library's
     * choice and a library upgrade is exactly the kind of change nobody reads a CBOM parser for.
     */
    @Test
    void theIngestMapperKeepsSourceTextOutOfParseFailures() {
        assertThat(ObjectMapperFactory
                .storage()
                .getFactory()
                .isEnabled(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION.mappedFeature()))
                .describedAs("a parse failure must not quote the document it failed on")
                .isFalse();
    }

    @Test
    void aParseFailureMessageCarriesNoDocumentContent() {
        String secret = "s3cr3t-material-value";
        String malformed = "{\"components\":[{\"value\":\"" + secret + "\",}]}";

        assertThatParsingFails(malformed, secret);
    }

    // ---------------------------------------------------------------- helpers

    private static void assertThatParsingFails(String malformed, String secret) {
        try {
            ObjectMapperFactory.storage().readTree(malformed);
            org.assertj.core.api.Assertions.fail("the fixture must be malformed for this test to mean anything");
        } catch (JsonProcessingException e) {
            assertThat(e.getMessage())
                    .describedAs("a parse failure must not quote the document")
                    .doesNotContain(secret);
        }
    }

    private static List<String> keysOf(List<String> components) {
        return keysOf(EXTRACTOR.extract(read("{\"components\":[" + String.join(",", components) + "]}")));
    }

    private static List<String> keysOf(CbomAssetExtractor.Extraction extraction) {
        return extraction.assets().stream().map(CbomAssetExtractor.ExtractedAsset::key).sorted().toList();
    }

    /** A component tree {@code depth} levels deep with one algorithm asset at the bottom. */
    private static String nestedDocument(int depth) {
        StringBuilder nested = new StringBuilder("{\"components\":[");
        for (int level = 0; level < depth; level++) {
            nested.append("{\"type\":\"library\",\"name\":\"n").append(level).append("\",\"components\":[");
        }
        nested.append(algorithm("AES-256")).append("]}".repeat(depth)).append("]}");
        return nested.toString();
    }

    private static String algorithm(String name) {
        return "{\"type\":\"cryptographic-asset\",\"name\":\"" + name + "\",\"cryptoProperties\":"
                + "{\"assetType\":\"algorithm\",\"algorithmProperties\":{}}}";
    }

    private static String certificate(String subject) {
        return "{\"type\":\"cryptographic-asset\",\"name\":\"cert-" + subject + "\",\"cryptoProperties\":"
                + "{\"assetType\":\"certificate\",\"certificateProperties\":{\"subjectName\":\"CN=" + subject
                + "\",\"issuerName\":\"CN=ca\"}}}";
    }

    private static JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("test fixture is not JSON", e);
        }
    }

    // ---------------------------------------------------------------- evidence

    /**
     * A credential in an occurrence location must not survive into stored evidence.
     *
     * <p>
     * The keying path already strips it, or a password would be hashed into the identity. The stored evidence is the
     * other half of the same rule and had no such step: capped and retained verbatim, the evidence column would hold
     * the credential the key was careful not to hash -- in a column the read surface serves back. This test fails
     * against the version of this walker that captured no evidence, and against any that captures it unsanitized.
     */
    @Test
    void aCredentialInALocationNeverReachesStoredEvidence() {
        JsonNode document = read("{\"components\":[{\"type\":\"cryptographic-asset\",\"name\":\"k\","
                + "\"cryptoProperties\":{\"assetType\":\"algorithm\"},"
                + "\"evidence\":{\"occurrences\":[{\"location\":\"tcp://user:hunter2@host:443/p?token=abc\","
                + "\"line\":7}]}}]}");

        CbomAssetExtractor.ExtractedAsset asset = EXTRACTOR.extract(document).assets().get(0);

        assertThat(asset.evidence()).hasSize(1);
        assertThat(asset.evidence().toString()).doesNotContain("hunter2").doesNotContain("token=abc");
        assertThat(asset.evidence().get(0)).containsEntry("location", "tcp://host:443/p").containsEntry("line", 7);
    }

    /**
     * The retained list is capped and the reported count is the pre-cap total, so the gap is the record that capping
     * happened. No separate flag is needed, and none can drift out of step with the array.
     */
    @Test
    void theCountIsThePreCapTotalWhileTheListIsBounded() {
        StringBuilder occurrences = new StringBuilder();
        int reported = OccurrenceEvidenceCapper.MAX_OCCURRENCES + 25;
        for (int index = 0; index < reported; index++) {
            occurrences.append(index > 0 ? "," : "").append("{\"location\":\"file").append(index).append(".java\"}");
        }
        JsonNode document = read("{\"components\":[{\"type\":\"cryptographic-asset\",\"name\":\"k\","
                + "\"cryptoProperties\":{\"assetType\":\"algorithm\"}," + "\"evidence\":{\"occurrences\":["
                + occurrences + "]}}]}");

        CbomAssetExtractor.ExtractedAsset asset = EXTRACTOR.extract(document).assets().get(0);

        assertThat(asset.reportedOccurrences()).isEqualTo(reported);
        assertThat(asset.evidence()).hasSize(OccurrenceEvidenceCapper.MAX_OCCURRENCES);
    }

    /**
     * No evidence reported is distinct from evidence that capping emptied, so it stays {@code null} rather than
     * becoming an empty list.
     */
    @Test
    void aSourceThatReportedNoEvidenceIsDistinctFromOneThatWasCapped() {
        CbomAssetExtractor.ExtractedAsset asset = EXTRACTOR
                .extract(read("{\"components\":[" + algorithm("AES-256") + "]}"))
                .assets()
                .get(0);

        assertThat(asset.evidence()).isNull();
        assertThat(asset.reportedOccurrences()).isZero();
    }
}
