package com.otilm.core.cbom.pqc;

import com.fasterxml.jackson.databind.JsonNode;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetType;
import com.otilm.api.model.core.cryptoasset.PqcVerdict;
import com.otilm.core.cbom.asset.CryptoAssetIdentityFields;
import com.otilm.core.cbom.asset.identity.AsciiText;
import com.otilm.core.cbom.asset.identity.AssetNormalizer;
import com.otilm.core.cbom.asset.identity.NormalizedAsset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Decides one asset's post-quantum readiness. Pure: storing the result is the caller's separate call, which is what
 * lets the whole rule set be tested without Spring or a database.
 *
 * <p>
 * Ingest holds a {@link NormalizedAsset}; the sweep holds only a stored row. Both go through {@link #fromStoredRow}, so
 * they cannot disagree about a field -- see it for the folding trap that made them disagree about hybrids.
 */
@Component
public class PqcEvaluator {

    /** The {@code -768} a hybrid component may carry, which the family tables do not spell. */
    private static final Pattern FAMILY_SIZE_SUFFIX = Pattern.compile("-\\d+$");

    private static final PqcRule HYBRID_RULE = new PqcRule("PQC-HYBRID", PqcRuleInput::isHybrid,
            com.otilm.api.model.core.cryptoasset.PqcVerdict.READY,
            "A hybrid construction; its readiness is that of its post-quantum component",
            List.of(PqcRules.ALGORITHM_FAMILY, PqcRules.HYBRID_COMPONENTS, PqcRules.NAME, "variant"));

    private static final String RELATED_MATERIAL = "relatedCryptoMaterialProperties";
    private static final String ALGORITHM_PROPERTIES = "algorithmProperties";

    private static final String NIST_LEVEL = PqcRules.NIST_QUANTUM_SECURITY_LEVEL;

    /** The ratified "the producer said nothing" spelling for a material type, per core#2196's ruling C10. */
    private static final String MATERIAL_TYPE_UNKNOWN = "unknown";

    /** The ratified spellings, indexed by their separator-insensitive lookup key. */
    private static final Map<String, String> MATERIAL_TYPE_KEYS = Stream
            .of(PqcRules.SYMMETRIC_MATERIAL, PqcRules.NON_KEY_MATERIAL, Set.of("private-key", "public-key", "key-pair"))
            .flatMap(Set::stream)
            .collect(java.util.stream.Collectors.toMap(AsciiText::lookupKey, spelling -> spelling));

    private final AssetNormalizer normalizer;
    private final java.util.List<PqcRule> rules;

    public PqcEvaluator(AssetNormalizer normalizer) {
        this.normalizer = normalizer;
        this.rules = PqcRules.rulesFor(normalizer);
    }

    /**
     * First match wins. @param nistQuantumSecurityLevel corroboration only; a parameter, so no predicate can reach it
     */
    public PqcDecision evaluate(PqcRuleInput input, Integer nistQuantumSecurityLevel) {
        for (PqcRule rule : rules) {
            if (rule.matches().test(input)) {
                return rule.id().equals("PQC-HYBRID")
                        ? hybridDecision(input, hybridComponentsOf(input), rule, nistQuantumSecurityLevel)
                        : decision(rule.verdict(), rule.id(), rule.reason(), rule.readsFields(), input,
                                nistQuantumSecurityLevel);
            }
        }
        List<String> undeclaredHybrid = hybridComponentsOf(input);
        if (!undeclaredHybrid.equals(input.hybridComponents()) && !undeclaredHybrid.isEmpty()) {
            return hybridDecision(input, undeclaredHybrid, HYBRID_RULE, nistQuantumSecurityLevel);
        }
        FamilyClass weakest = weakestSecondary(input);
        if (weakest != null) {
            return decision(weakest.verdict(), "CLASSICAL-LEGACY-COMPONENT",
                    "A component named by this asset is already broken classically, so the construction inherits it",
                    List.of(PqcRules.ALGORITHM_FAMILY, "variant", PqcRules.NAME), input, nistQuantumSecurityLevel);
        }
        return familyDecision(input, nistQuantumSecurityLevel);
    }

    /**
     * The hybrid components the grammar recorded, widened by the secondary tokens.
     *
     * <p>
     * {@code AssetNormalizer.hybridComponents} tests membership against its own {@code PQC_FAMILIES}, which holds 25
     * tokens where the ratified tables name 33 pseudo-families -- the drift core#2196's ruling C12 exists to end. So
     * {@code X25519-HAWK-512} recorded no components, elected its classical half and read {@code notReady} on that half
     * alone, which is the one outcome ruling (b) forbids. The ratified tables answer for all 33.
     */
    private List<String> hybridComponentsOf(PqcRuleInput input) {
        if (!input.hybridComponents().isEmpty()) {
            return input.hybridComponents();
        }
        if (PqcFamilies.of(ratifiedFamily(input.algorithmFamily())) != FamilyClass.SHOR_BREAKABLE) {
            return List.of();
        }
        List<String> components = new java.util.ArrayList<>();
        for (String token : secondaryTokens(input)) {
            if (isPostQuantum(dispositionOfToken(token))) {
                components.add(input.algorithmFamily());
                components.add(token);
            }
        }
        return List.copyOf(components);
    }

    /**
     * The weakest disposition among the secondary tokens, when one of them is classically broken.
     *
     * <p>
     * {@code HMAC-MD5} elects family {@code HMAC} and stores {@code md5} in the variant slot; {@code 3DES-CMAC} elects
     * {@code CMAC} and stores {@code 3des,des}. Reading the family alone answered {@code ready} for both. The
     * specification made those tokens identity-bearing precisely because dropping them "silently erases a weak-crypto
     * finding -- the one outcome an inventory must never produce", and the verdict path was doing exactly that.
     */
    private FamilyClass weakestSecondary(PqcRuleInput input) {
        for (String token : secondaryTokens(input)) {
            if (dispositionOfToken(token) == FamilyClass.CLASSICAL_LEGACY) {
                return FamilyClass.CLASSICAL_LEGACY;
            }
        }
        return null;
    }

    private List<String> secondaryTokens(PqcRuleInput input) {
        if (input.variant() == null || input.variant().isEmpty()) {
            return List.of();
        }
        return java.util.Arrays.stream(input.variant().split(",")).filter(token -> !token.isEmpty()).toList();
    }

    /**
     * A hybrid's readiness is its post-quantum component's, not the fact that it has one: {@code X25519-ML-KEM-768} is
     * ready, {@code X25519-Kyber768} is not, because bare Kyber is a superseded draft. What holds unconditionally is
     * that the classical half never decides.
     */
    private PqcDecision hybridDecision(PqcRuleInput input, List<String> components, PqcRule rule,
            Integer nistQuantumSecurityLevel) {
        if (components.stream().noneMatch(this::isShorBreakable)) {
            // Not a hybrid, whatever the grammar recorded. AssetNormalizer counts any non-PQC secondary token as the
            // classical half, and every FIPS 205 and RFC 8391 parameter-set name carries a hash token -- so
            // SLH-DSA-SHAKE-256f and XMSSMT-SHA2_20/2_256 arrived here as hybrids. The verdict was right by luck,
            // because the post-quantum half wins either way, but the rule id and reason on the wire were false and an
            // operator filtering on PQC-STANDARDIZED missed every standards-spelled row.
            return familyDecision(input, nistQuantumSecurityLevel);
        }
        FamilyClass strongest = null;
        for (String component : components) {
            FamilyClass disposition = PqcFamilies
                    .of(ratifiedFamily(FAMILY_SIZE_SUFFIX.matcher(component).replaceFirst("")));
            if (disposition == FamilyClass.PQC_STANDARDIZED) {
                strongest = disposition;
                break;
            }
            if (isPostQuantum(disposition) && strongest == null) {
                strongest = disposition;
            }
        }
        if (strongest == null) {
            // Recorded as hybrid by the grammar, but no component resolves to a post-quantum family this table knows.
            // Not notReady: the classical half must not decide a hybrid, which is the one thing ruling (b) settles.
            return decision(PqcVerdict.UNKNOWN, "PQC-HYBRID-UNRESOLVED",
                    "A hybrid construction whose post-quantum component resolves to no ratified family",
                    rule.readsFields(), input, nistQuantumSecurityLevel);
        }
        String reason = strongest == FamilyClass.PQC_STANDARDIZED
                ? rule.reason()
                : "A hybrid construction whose post-quantum component is not standardised: " + strongest.reason();
        return decision(strongest.verdict(), rule.id() + "-" + strongest.ruleId(), reason, rule.readsFields(), input,
                nistQuantumSecurityLevel);
    }

    private boolean isShorBreakable(String component) {
        return PqcFamilies
                .of(ratifiedFamily(
                        FAMILY_SIZE_SUFFIX.matcher(component).replaceFirst(""))) == FamilyClass.SHOR_BREAKABLE;
    }

    private static boolean isPostQuantum(FamilyClass disposition) {
        return disposition == FamilyClass.PQC_STANDARDIZED || disposition == FamilyClass.PQC_PRESTANDARD
                || disposition == FamilyClass.PQC_BROKEN || disposition == FamilyClass.PQC_HYBRID;
    }

    private PqcDecision familyDecision(PqcRuleInput input, Integer nistQuantumSecurityLevel) {
        FamilyClass disposition = PqcFamilies.of(ratifiedFamily(input.algorithmFamily()));
        if (disposition == null) {
            return decision(PqcVerdict.UNKNOWN, PqcRules.FAMILY_UNRESOLVED,
                    "The recorded properties resolve to no ratified algorithm family, so no rule can classify it",
                    List.of(PqcRules.ALGORITHM_FAMILY, PqcRules.NAME), input, nistQuantumSecurityLevel);
        }
        return decision(disposition.verdict(), disposition.ruleId(), disposition.reason(),
                List.of(PqcRules.ALGORITHM_FAMILY, PqcRules.PARAMETER_SET), input, nistQuantumSecurityLevel);
    }

    /**
     * Any spelling onto the ratified one, and not optional: the column is case-folded while {@code AssetNormalizer}
     * compares family tokens case-sensitively, so passing a folded {@code x-wing} straight back took the non-hybrid
     * path and re-derived different components.
     */
    private String ratifiedFamily(String anySpelling) {
        return normalizer.tables().familyToken(anySpelling);
    }

    /**
     * A component token onto its ratified family, whole spelling first.
     *
     * <p>
     * The size suffix is stripped only as a fallback, because several families carry a digit that is part of the name:
     * stripping first turns {@code sha-1} into {@code sha} and {@code sha-2} into {@code sha}, which resolve to nothing
     * -- so {@code HMAC-SHA1} read {@code ready}. The normalizer documents the same trap on its own token folding.
     */
    private FamilyClass dispositionOfToken(String token) {
        FamilyClass whole = PqcFamilies.of(ratifiedFamily(token));
        if (whole != null) {
            return whole;
        }
        return PqcFamilies.of(ratifiedFamily(FAMILY_SIZE_SUFFIX.matcher(token).replaceFirst("")));
    }

    // ---- The two input shapes -------------------------------------------------------------------------------------

    /**
     * Delegates rather than reading the derivation, because a verdict describes the row that will be stored. Reusing
     * {@link NormalizedAsset#hybridComponents()} looked equivalent: they come from the raw name, while the sweep
     * re-derives from the folded column, so a name with fullwidth digits produced different evidence on each side.
     */
    public PqcRuleInput fromNormalized(NormalizedAsset asset, JsonNode cryptoProperties) {
        return fromStoredRow(CryptoAssetIdentityFields.of(assetTypeOf(asset.assetType()), asset).normalized(),
                cryptoProperties);
    }

    /** {@code hybridComponents} is re-derived, not read: it is out-of-key by construction and has no column. */
    public PqcRuleInput fromStoredRow(CryptoAssetIdentityFields fields, JsonNode mergedCryptoProperties) {
        String family = ratifiedFamily(fields.algorithmFamily());
        List<String> hybrid = normalizer.hybridComponents(family, normalizer.secondaryTokens(fields.name(), family));
        return new PqcRuleInput(fields.assetType(), family, parameterSet(fields.parameterSet()), fields.curve(),
                fields.mode(), fields.padding(), fields.variant(), fields.name(), hybrid,
                materialType(mergedCryptoProperties), materialSize(mergedCryptoProperties));
    }

    public static CryptographicAssetType assetTypeOf(String routed) {
        if (routed == null) {
            return null;
        }
        return switch (routed) {
            case "algorithm" -> CryptographicAssetType.ALGORITHM;
            case "certificate" -> CryptographicAssetType.CERTIFICATE;
            case "protocol" -> CryptographicAssetType.PROTOCOL;
            case "related-crypto-material" -> CryptographicAssetType.RELATED_CRYPTO_MATERIAL;
            default -> CryptographicAssetType.UNROUTABLE;
        };
    }

    private static Integer parameterSet(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            return Integer.valueOf(stored.trim());
        } catch (NumberFormatException e) {
            // The column is text and a producer can put anything in it. A parameter set that is not a number cannot
            // participate in a size comparison, and reading it as absent is what the ingest shape already does.
            return null;
        }
    }

    /**
     * The ratified {@code unknown} spelling reads as absent, and lookup is separator-insensitive because producers
     * camelCase these vocabularies. core#2196's ruling C10 keeps {@code unknown} as a value; this is its second reader,
     * and takes it as "the producer said nothing" rather than a fourth arm of the material partition.
     */
    static String materialType(JsonNode cryptoProperties) {
        JsonNode material = cryptoProperties == null ? null : cryptoProperties.get(RELATED_MATERIAL);
        if (material == null || !material.isObject()) {
            return null;
        }
        JsonNode type = material.get("type");
        if (type == null || !type.isTextual() || type.textValue().isBlank()) {
            return null;
        }
        // lookupKey, not toLowerCase: producers camelCase these vocabularies, and MaterialRedaction already matches
        // them separator-insensitively. `secretKey` and `SECRET_KEY` must reach the same arm as `secret-key`.
        String folded = AsciiText.lookupKey(type.textValue());
        if (folded.isEmpty() || AsciiText.lookupKey(MATERIAL_TYPE_UNKNOWN).equals(folded)) {
            return null;
        }
        return MATERIAL_TYPE_KEYS.getOrDefault(folded, folded);
    }

    static Integer materialSize(JsonNode cryptoProperties) {
        JsonNode material = cryptoProperties == null ? null : cryptoProperties.get(RELATED_MATERIAL);
        if (material == null || !material.isObject()) {
            return null;
        }
        JsonNode size = material.get("size");
        // canConvertToInt, not intValue: a size of 4294967424 truncates to 128 and reads as an adequate key.
        return size != null && size.isIntegralNumber() && size.canConvertToInt() ? size.intValue() : null;
    }

    /** Non-integral reads as absent: one producer wrote a string there, and the wire field promises a level. */
    public static Integer nistQuantumSecurityLevel(JsonNode cryptoProperties) {
        JsonNode algorithm = cryptoProperties == null ? null : cryptoProperties.get(ALGORITHM_PROPERTIES);
        if (algorithm == null || !algorithm.isObject()) {
            return null;
        }
        JsonNode level = algorithm.get(NIST_LEVEL);
        return level != null && level.isIntegralNumber() ? level.intValue() : null;
    }

    // ---- Evidence -------------------------------------------------------------------------------------------------

    /** Projected from a closed switch, never collected by watching what the predicate read. */
    private static PqcDecision decision(PqcVerdict verdict, String ruleId, String reason, List<String> readsFields,
            PqcRuleInput input, Integer nistQuantumSecurityLevel) {
        return new PqcDecision(verdict, ruleId, reason,
                projectEvidence(readsFields, input, nistQuantumSecurityLevel, ruleId));
    }

    static Map<String, Object> projectEvidence(List<String> readsFields, PqcRuleInput input,
            Integer nistQuantumSecurityLevel, String ruleId) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        for (String field : readsFields) {
            if (!PqcRules.EVIDENCE_FIELDS.contains(field)) {
                throw new IllegalStateException(
                        "Rule " + ruleId + " declares an evaluated field outside the allowlist: " + field);
            }
            Object value = valueOf(field, input, nistQuantumSecurityLevel);
            if (value != null) {
                evidence.put(field, value);
            }
        }
        if (nistQuantumSecurityLevel != null) {
            // Recorded on every decision, never read by one: it is the corroboration an operator can check the verdict
            // against, and keeping it out of the rules is what stops a producer's disagreement from moving a verdict.
            evidence.put(PqcRules.NIST_QUANTUM_SECURITY_LEVEL, nistQuantumSecurityLevel);
        }
        return evidence;
    }

    private static Object valueOf(String field, PqcRuleInput input, Integer nistQuantumSecurityLevel) {
        return switch (field) {
            case PqcRules.ASSET_TYPE -> input.assetType() == null ? null : input.assetType().getCode();
            case PqcRules.ALGORITHM_FAMILY -> input.algorithmFamily();
            case PqcRules.PARAMETER_SET -> input.parameterSet();
            case "curve" -> input.curve();
            case "mode" -> input.mode();
            case "padding" -> input.padding();
            case "variant" -> input.variant();
            case PqcRules.NAME -> input.name();
            case PqcRules.HYBRID_COMPONENTS -> input.hybridComponents().isEmpty() ? null : input.hybridComponents();
            case PqcRules.MATERIAL_TYPE -> input.materialType();
            case PqcRules.MATERIAL_SIZE -> input.materialSize();
            case PqcRules.NIST_QUANTUM_SECURITY_LEVEL -> nistQuantumSecurityLevel;
            default -> throw new IllegalStateException("Unhandled evaluated field: " + field);
        };
    }
}
