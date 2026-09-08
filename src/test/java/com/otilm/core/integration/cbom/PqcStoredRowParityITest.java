package com.otilm.core.integration.cbom;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otilm.api.model.core.cryptoasset.PqcVerdict;
import com.otilm.core.cbom.asset.CryptoAssetIdentityFields;
import com.otilm.core.cbom.asset.identity.AssetNormalizer;
import com.otilm.core.cbom.asset.identity.CryptoAssetIdentity;
import com.otilm.core.cbom.asset.identity.NormalizedAsset;
import com.otilm.core.cbom.pqc.PqcDecision;
import com.otilm.core.cbom.pqc.PqcEvaluator;
import com.otilm.core.cbom.pqc.PqcRules;
import com.otilm.core.cbom.pqc.PqcRuleset;
import com.otilm.core.dao.entity.Cbom;
import com.otilm.core.dao.entity.cbom.CryptoAsset;
import com.otilm.core.dao.repository.CbomRepository;
import com.otilm.core.dao.repository.cbom.CryptoAssetRepository;
import com.otilm.core.service.writer.cbom.CryptoAssetSourceWriter;
import com.otilm.core.service.writer.cbom.CryptoAssetWriter;
import com.otilm.core.util.BaseSpringBootTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The verdict the sweep reads off a stored row against the verdict ingest recorded, over a row written by the shipped
 * writer and read back from PostgreSQL, so the two sides share only the rule set.
 *
 * <p>
 * Each single-producer vector also asserts the rule id the rule set owes the asset. That oracle is not redundant: a
 * derivation that drops a column leaves both sides agreeing on {@code FAMILY-UNRESOLVED}, which a parity assertion
 * alone cannot see.
 */
class PqcStoredRowParityITest extends BaseSpringBootTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> PAYLOAD = new TypeReference<>() {
    };

    @Autowired
    private CryptoAssetRepository assetRepository;

    @Autowired
    private CbomRepository cbomRepository;

    @Autowired
    private CryptoAssetWriter assetWriter;

    @Autowired
    private CryptoAssetSourceWriter sourceWriter;

    @Autowired
    private AssetNormalizer normalizer;

    @Autowired
    private PqcEvaluator evaluator;

    private CryptoAssetIdentity identity;
    private Cbom firstCbom;
    private Cbom secondCbom;

    @BeforeEach
    void seed() {
        identity = new CryptoAssetIdentity(normalizer);
        firstCbom = cbom("urn:uuid:first");
        secondCbom = cbom("urn:uuid:second");
    }

    static Stream<Arguments> singleProducerVectors() {
        return Stream
                .of(Arguments.of(algorithm("RSA-2048"), "CLASSICAL-SHOR"),
                        Arguments.of(algorithm("X25519-ML-KEM-768"), "PQC-HYBRID-PQC-STANDARDIZED"),
                        Arguments.of(algorithm("X25519-ML-KEM-７６８"), "PQC-HYBRID-PQC-STANDARDIZED"),
                        Arguments.of(algorithm("AES-256-GCM"), "SYMMETRIC-READY"),
                        Arguments.of(algorithm("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"), "NAME-CIPHER-SUITE"),
                        Arguments
                                .of(material("vault-key-2024", "\"id\":\"vault-key-2024\",\"size\":256"),
                                        "MATERIAL-SYMMETRIC-READY"));
    }

    @ParameterizedTest
    @MethodSource("singleProducerVectors")
    void aRowWrittenByOneProducerDecidesAsIngestDid(JsonNode component, String expectedRuleId) {
        CryptoAsset row = asset(ingest(component, firstCbom));

        PqcDecision sweep = sweep(row);

        assertThat(row.getPqcRuleId())
                .describedAs("ingest must have decided what the rule set owes this asset")
                .isEqualTo(expectedRuleId);
        assertThat(sweep.ruleId()).isEqualTo(row.getPqcRuleId());
        assertThat(sweep.verdict()).isEqualTo(row.getPqcVerdict());
        JsonNode sweepEvidence = MAPPER.valueToTree(sweep.evaluatedFields());
        JsonNode recordedEvidence = MAPPER.valueToTree(row.getPqcEvaluatedFields());
        assertThat(sweepEvidence).isEqualTo(recordedEvidence);
    }

    /**
     * The key path collapses internal whitespace runs where the column's fold does not, so the two spellings share one
     * row; {@code COALESCE} then keeps the first, which is what makes re-sync idempotent.
     */
    @Test
    void theRowKeepsTheFirstProducersNameWhileIngestEvaluatedTheSecond() {
        UUID first = ingest(algorithm("private key"), firstCbom);
        UUID second = ingest(algorithm("private  key"), secondCbom);
        assertThat(second).describedAs("the two spellings key alike").isEqualTo(first);

        CryptoAsset row = asset(second);
        PqcDecision sweep = sweep(row);

        assertThat(row.getName()).isEqualTo("private key");
        assertThat(row.getPqcRuleId())
                .describedAs("ingest evaluated the second producer's spelling, which the name list does not hold")
                .isEqualTo(PqcRules.FAMILY_UNRESOLVED);
        assertThat(row.getPqcVerdict()).isEqualTo(PqcVerdict.UNKNOWN);
        assertThat(sweep.ruleId())
                .describedAs("the sweep reads the first producer's spelling, which it does")
                .isEqualTo("NAME-NOT-AN-ALGORITHM");
        assertThat(sweep.verdict()).isEqualTo(PqcVerdict.NOT_APPLICABLE);
    }

    /**
     * {@code size} is outside the material identity tuple, so producers disagreeing about it share one row, and the
     * merge elects the richest payload rather than the one ingest read.
     */
    @Test
    void theMergeElectsTheRichestPayloadWhileIngestEvaluatedTheThinOne() {
        UUID rich = ingest(material("vault-key-2024",
                "\"id\":\"vault-key-2024\",\"size\":64,\"state\":\"active\",\"format\":\"raw\""), firstCbom);
        UUID thin = ingest(material("vault-key-2024", "\"id\":\"vault-key-2024\",\"size\":256"), secondCbom);
        assertThat(thin).describedAs("size is not part of the material identity").isEqualTo(rich);

        CryptoAsset row = asset(thin);
        PqcDecision sweep = sweep(row);

        assertThat(row.getSourceCount()).isEqualTo(2);
        assertThat(elected(row).get("size").intValue()).isEqualTo(64);
        assertThat(row.getPqcRuleId())
                .describedAs("ingest evaluated the thin producer's own payload")
                .isEqualTo("MATERIAL-SYMMETRIC-READY");
        assertThat(row.getPqcVerdict()).isEqualTo(PqcVerdict.READY);
        assertThat(sweep.ruleId())
                .describedAs("the sweep reads the elected payload; 64 is inside the ratified size band")
                .isEqualTo("MATERIAL-SYMMETRIC-WEAK");
        assertThat(sweep.verdict()).isEqualTo(PqcVerdict.NOT_READY);
    }

    // ---- the two sides ----

    /** Key the component, upsert identity and source, evaluate the derivation, record the verdict. */
    private UUID ingest(JsonNode component, Cbom cbom) {
        CryptoAssetIdentity.Identity keyed = identity.of(component);
        NormalizedAsset asset = keyed.asset();
        JsonNode payload = keyed.redaction().storedPayload();

        UUID assetUuid = assetWriter
                .upsertIdentity(keyed.key(),
                        CryptoAssetIdentityFields.of(PqcEvaluator.assetTypeOf(asset.assetType()), asset),
                        keyed.guard());
        sourceWriter
                .upsertSource(assetUuid, cbom.getUuid(), MAPPER.convertValue(payload, PAYLOAD), List.of(),
                        OffsetDateTime.now());

        PqcDecision decision = evaluator
                .evaluate(evaluator.fromNormalized(asset, payload), PqcEvaluator.nistQuantumSecurityLevel(payload));
        assetWriter
                .applyPqcVerdict(assetUuid, decision.verdict(), decision.ruleId(), decision.reason(),
                        PqcRuleset.VERSION, decision.evaluatedFields());
        return assetUuid;
    }

    /** The sweep's view: nothing but the columns and the elected payload that came back from the database. */
    private PqcDecision sweep(CryptoAsset row) {
        CryptoAssetIdentityFields stored = new CryptoAssetIdentityFields(row.getAssetType(), row.getName(),
                row.getOid(), row.getAlgorithmFamily(), row.getPrimitive(), row.getParameterSet(), row.getCurve(),
                row.getMode(), row.getPadding(), row.getVariant());
        JsonNode merged = row.getMergedCryptoProperties() == null
                ? null
                : MAPPER.valueToTree(row.getMergedCryptoProperties());
        return evaluator
                .evaluate(evaluator.fromStoredRow(stored, merged), PqcEvaluator.nistQuantumSecurityLevel(merged));
    }

    private static JsonNode elected(CryptoAsset row) {
        return MAPPER.valueToTree(row.getMergedCryptoProperties()).get("relatedCryptoMaterialProperties");
    }

    // ---- fixtures ----

    private CryptoAsset asset(UUID uuid) {
        return assetRepository.findById(uuid).orElseThrow();
    }

    private Cbom cbom(String serialNumber) {
        Cbom cbom = new Cbom();
        cbom.setSerialNumber(serialNumber);
        cbom.setVersion(1);
        cbom.setSpecVersion("1.7");
        return cbomRepository.save(cbom);
    }

    private static JsonNode algorithm(String name) {
        return component(name, "{\"assetType\":\"algorithm\",\"algorithmProperties\":{}}");
    }

    private static JsonNode material(String name, String materialMembers) {
        return component(name, "{\"assetType\":\"related-crypto-material\",\"relatedCryptoMaterialProperties\":{"
                + "\"type\":\"secret-key\"," + materialMembers + "}}");
    }

    private static JsonNode component(String name, String cryptoProperties) {
        try {
            return MAPPER
                    .readTree("{\"type\":\"cryptographic-asset\",\"name\":\"" + name + "\",\"cryptoProperties\":"
                            + cryptoProperties + "}");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
