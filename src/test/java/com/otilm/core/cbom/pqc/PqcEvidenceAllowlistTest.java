package com.otilm.core.cbom.pqc;

import com.otilm.core.cbom.asset.JsonColumnText;
import com.otilm.core.cbom.asset.identity.AssetNormalizer;
import com.otilm.core.cbom.asset.identity.IdentityTables;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The write side of the identity-key constraint, asserted directly because nothing else can.
 *
 * <p>
 * {@code crypto_asset.pqc_evaluated_fields} is served verbatim to clients as
 * {@code CryptographicAssetVerdictDto.evaluatedFields}, and {@code IdentityKeyExposureFence} cannot see into it: the
 * fence is lexical over source text, both ends of this channel are innocuously named -- {@code pqcEvaluatedFields},
 * {@code evaluatedFields}, typed {@code Map<String, Object>} -- and the map's keys are runtime data. A green build
 * therefore proves nothing about what travels in this column, which is why these assertions run over the rendered JSON
 * rather than over the code.
 */
class PqcEvidenceAllowlistTest {

    /** The fence's own vocabulary, applied here to values rather than to declarations. */
    private static final Pattern FENCED = Pattern
            .compile("identity[_\\-\\s]?key|canonical[_\\-\\s]?key|absorbed[_\\-\\s]?key", Pattern.CASE_INSENSITIVE);

    private final AssetNormalizer normalizer = new AssetNormalizer(IdentityTables.load());
    private final PqcEvaluator evaluator = new PqcEvaluator(normalizer);

    @Test
    void everyRuleDeclaresOnlyAllowlistedFields() {
        assertThat(PqcRules.rulesFor(normalizer))
                .allSatisfy(rule -> assertThat(PqcRules.EVIDENCE_FIELDS)
                        .describedAs("rule %s declares %s", rule.id(), rule.readsFields())
                        .containsAll(rule.readsFields()));
    }

    @ParameterizedTest
    @MethodSource("com.otilm.core.cbom.pqc.PqcParityTest#assets")
    void nothingOutsideTheAllowlistReachesTheColumn(String name) {
        PqcDecision decision = decide(name);
        assertThat(decision.evaluatedFields().keySet())
                .describedAs("evidence keys for %s", name)
                .isSubsetOf(PqcRules.EVIDENCE_FIELDS);
    }

    /**
     * The assertion the fence cannot make: over the bytes the column receives, not over the field names in source.
     *
     * <p>
     * <b>What is guaranteed, stated precisely.</b> Evidence values are producer text -- a name, a family, a curve --
     * and an earlier revision of this test asserted no rendered evidence could contain 64 hex characters, which reads
     * as "no producer string reaches the client" and is false: every value recorded here is drawn from a column the
     * detail endpoint already serves under its own name, so echoing one discloses nothing new. What must never appear
     * is a value <em>derived from</em> {@code crypto_asset.identity_key}, an alias canonical key or an absorbed key,
     * because core#2070's redaction ruling rests on those never leaving the database and the key is a hash over a
     * low-entropy pre-image. {@link #theRuleInputTypeExposesNoKeyBearingComponent} is what settles that, structurally.
     * The adversarial case below records the difference rather than pretending it away.
     */
    @ParameterizedTest
    @MethodSource("com.otilm.core.cbom.pqc.PqcParityTest#assets")
    void noRenderedEvidenceNamesAKeyBearingField(String name) {
        PqcDecision decision = decide(name);
        assertThat(JsonColumnText.render(decision.evaluatedFields())).isNotNull();
        // The keys, not the values: the keys are ours and closed, the values are producer text that a producer can
        // spell however it likes. Asserting the regex over the whole rendered blob reads as a guarantee about the
        // values, and anAdversarialProducerNameIsEchoedRatherThanLeaking shows that guarantee does not exist.
        assertThat(decision.evaluatedFields().keySet())
                .describedAs("evidence keys for %s", name)
                .allSatisfy(key -> assertThat(FENCED.matcher(key).find()).isFalse());
    }

    /**
     * A producer that names its asset like an identity key gets its own string back, and that is not a disclosure: the
     * same text is already served as the asset's {@code name}. Pinned so nobody re-derives the stronger claim from the
     * weaker test above.
     */
    @Test
    void anAdversarialProducerNameIsEchoedRatherThanLeaking() {
        String keyShaped = "identity key " + "a".repeat(64);
        PqcDecision decision = decide(keyShaped);

        assertThat(decision.ruleId()).isEqualTo(PqcRules.FAMILY_UNRESOLVED);
        assertThat(decision.evaluatedFields())
                .describedAs("the producer's own name, folded, and nothing else")
                .containsEntry("name", keyShaped.toLowerCase(java.util.Locale.ROOT));
        assertThat(decision.evaluatedFields().keySet()).isSubsetOf(PqcRules.EVIDENCE_FIELDS);
    }

    /**
     * The input type carries no key to begin with, which is what makes the guarantee structural rather than a filter
     * someone has to remember to update. Stated as a test so that adding one to the record fails here.
     */
    @Test
    void theRuleInputTypeExposesNoKeyBearingComponent() {
        List<String> components = Arrays
                .stream(PqcRuleInput.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(components)
                .describedAs("no component may name a key, and the projection can only reach what this record holds")
                .isNotEmpty()
                .allSatisfy(component -> assertThat(FENCED.matcher(component).find()).isFalse())
                .allMatch(PqcRules.EVIDENCE_FIELDS::contains);
    }

    /**
     * A rule declaring a field outside the closed set is a programming error, and fails loudly rather than silently.
     */
    @Test
    void aRuleDeclaringAnUnknownFieldIsRefused() {
        assertThat(PqcRules.EVIDENCE_FIELDS).doesNotContain("identityKey");
        PqcRuleInput input = evaluator
                .fromNormalized(normalizer.normalize(PqcEvaluatorTest.algorithm("RSA-2048")).asset(), null);
        List<String> rogueFields = List.of("identityKey");

        // The evaluator's own check, not AssertJ's. An earlier revision asserted that containsAll threw, which tested
        // AssertJ and would have passed with the production guard deleted.
        assertThatThrownBy(() -> PqcEvaluator.projectEvidence(rogueFields, input, null, "ROGUE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identityKey");
    }

    private PqcDecision decide(String name) {
        var component = PqcEvaluatorTest.algorithm(name);
        var properties = component.get("cryptoProperties");
        return evaluator
                .evaluate(evaluator.fromNormalized(normalizer.normalize(component).asset(), properties),
                        PqcEvaluator.nistQuantumSecurityLevel(properties));
    }
}
