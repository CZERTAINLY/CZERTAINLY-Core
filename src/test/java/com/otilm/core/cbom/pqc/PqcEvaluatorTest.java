package com.otilm.core.cbom.pqc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetType;
import com.otilm.api.model.core.cryptoasset.PqcVerdict;
import com.otilm.core.cbom.asset.identity.AssetNormalizer;
import com.otilm.core.cbom.asset.identity.IdentityTables;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

/**
 * The rule set's answers, driven through the real normalizer rather than through hand-built inputs.
 *
 * <p>
 * Every case starts from a CBOM component and runs the shipped derivation over it, because the interesting failures are
 * in what the derivation hands the rules -- a hybrid whose family is its classical half, a suite name that elects no
 * family -- and a hand-built {@link PqcRuleInput} would assert only that the rule table says what it says.
 */
class PqcEvaluatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AssetNormalizer normalizer = new AssetNormalizer(IdentityTables.load());
    private final PqcEvaluator evaluator = new PqcEvaluator(normalizer);

    // ---- the migration surface ------------------------------------------------------------------------------------

    @Test
    void everyRsaAssetIsNotReady() {
        assertThat(verdictOf(algorithm("RSA-2048")).verdict()).isEqualTo(PqcVerdict.NOT_READY);
        assertThat(verdictOf(algorithm("RSA")).verdict()).isEqualTo(PqcVerdict.NOT_READY);
        assertThat(verdictOf(algorithm("RSASSA-PSS")).verdict()).isEqualTo(PqcVerdict.NOT_READY);
        assertThat(verdictOf(algorithm("RSA-2048")).ruleId()).isEqualTo("CLASSICAL-SHOR");
    }

    @Test
    void ellipticCurveIsNotReady() {
        assertThat(verdictOf(algorithm("ECDSA-P-256")).verdict()).isEqualTo(PqcVerdict.NOT_READY);
        assertThat(verdictOf(algorithm("Ed25519")).verdict()).isEqualTo(PqcVerdict.NOT_READY);
    }

    @Test
    void modernSymmetricAndHashAreReady() {
        assertThat(verdictOf(algorithm("AES-256-GCM")).verdict()).isEqualTo(PqcVerdict.READY);
        assertThat(verdictOf(algorithm("SHA-256")).verdict()).isEqualTo(PqcVerdict.READY);
        assertThat(verdictOf(algorithm("AES-256-GCM")).ruleId()).isEqualTo("SYMMETRIC-READY");
    }

    /**
     * An adjudication this rule set makes rather than inherits: reporting DES as post-quantum ready is true and
     * useless, so a classically broken primitive is not ready either -- under its own rule id, because the migration it
     * needs is a different one.
     */
    @Test
    void classicallyBrokenSymmetricIsNotReadyUnderItsOwnRuleId() {
        assertThat(verdictOf(algorithm("DES")).verdict()).isEqualTo(PqcVerdict.NOT_READY);
        assertThat(verdictOf(algorithm("DES")).ruleId()).isEqualTo("CLASSICAL-LEGACY");
        assertThat(verdictOf(algorithm("MD5")).ruleId()).isEqualTo("CLASSICAL-LEGACY");
    }

    // ---- post-quantum ----------------------------------------------------------------------------------------------

    @Test
    void standardisedPostQuantumIsReady() {
        assertThat(verdictOf(algorithm("ML-KEM-768")).verdict()).isEqualTo(PqcVerdict.READY);
        assertThat(verdictOf(algorithm("ML-DSA-65")).verdict()).isEqualTo(PqcVerdict.READY);
        assertThat(verdictOf(algorithm("ML-KEM-768")).ruleId()).isEqualTo("PQC-STANDARDIZED");
    }

    @Test
    void preStandardCandidatesAreNotReady() {
        assertThat(verdictOf(algorithm("Kyber768")).verdict()).isEqualTo(PqcVerdict.NOT_READY);
        assertThat(verdictOf(algorithm("Kyber768")).ruleId()).isEqualTo("PQC-PRESTANDARD");
        assertThat(verdictOf(algorithm("SIKEp434")).ruleId()).isEqualTo("PQC-BROKEN");
    }

    // ---- hybrids: the case the family column cannot answer alone ---------------------------------------------------

    /**
     * The rule that ruling (b) exists for. The identity grammar elects the classical half as the stored family, so a
     * family-first rule set would report a migrated asset as un-migrated.
     */
    @Test
    void aHybridIsNeverNotReadyOnItsClassicalHalf() {
        PqcDecision decision = verdictOf(algorithm("X25519-ML-KEM-768"));
        assertThat(decision.verdict()).isEqualTo(PqcVerdict.READY);
        assertThat(decision.ruleId()).startsWith("PQC-HYBRID");
        assertThat(decision.evaluatedFields()).containsKey("hybridComponents");
    }

    /**
     * And the case a presence-only hybrid rule would get wrong: the post-quantum half is a superseded draft, not
     * wire-compatible with the scheme that replaced it, so the hybrid inherits its disposition rather than a blanket
     * ready.
     */
    @Test
    void aHybridInheritsItsPostQuantumComponentsDisposition() {
        PqcDecision decision = verdictOf(algorithm("X25519-Kyber768"));
        assertThat(decision.verdict())
                .describedAs("a hybrid over a pre-standard draft is not a completed migration")
                .isEqualTo(PqcVerdict.NOT_READY);
        assertThat(decision.ruleId()).isEqualTo("PQC-HYBRID-PQC-PRESTANDARD");
    }

    // ---- correctly outside the question ----------------------------------------------------------------------------

    @Test
    void aCipherSuiteNameIsNotApplicableRatherThanAnUnknownMiss() {
        PqcDecision decision = verdictOf(algorithm("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"));
        assertThat(decision.verdict()).isEqualTo(PqcVerdict.NOT_APPLICABLE);
        assertThat(decision.ruleId()).isEqualTo("NAME-CIPHER-SUITE");
    }

    @Test
    void certificatesAreDeferredUnderTheirOwnRuleId() {
        PqcDecision decision = verdictOf(component("certificate", "www.example.test", "{}"));
        assertThat(decision.verdict()).isEqualTo(PqcVerdict.NOT_APPLICABLE);
        assertThat(decision.ruleId())
                .describedAs("a deferral must be queryable apart from a genuine not-an-algorithm")
                .isEqualTo("CERT-DEFERRED-V1");
    }

    @Test
    void protocolsAreNotApplicable() {
        assertThat(verdictOf(component("protocol", "TLSv1.3", "{}")).ruleId()).isEqualTo("PROTOCOL-NOT-ALGORITHM");
    }

    // ---- related cryptographic material ----------------------------------------------------------------------------

    /**
     * The material tier derives no family -- {@code AssetNormalizer} leaves it null for every
     * {@code related-crypto-material} component, with or without an {@code algorithmRef} -- so a private key whose own
     * name said {@code RSA-2048} reached the rules with nothing to classify and landed on {@code unknown}, against the
     * acceptance criterion that every RSA-2048 asset is {@code notReady}. The name is a column, so the evaluator reads
     * the family out of it, identically on both input shapes.
     */
    @Test
    void keyMaterialIsClassifiedByTheFamilyItsNameCarries() {
        PqcDecision privateKey = verdictOf(material("RSA-2048", "private-key", 2048));
        assertThat(privateKey.verdict()).isEqualTo(PqcVerdict.NOT_READY);
        assertThat(privateKey.ruleId()).isEqualTo("CLASSICAL-SHOR");
        assertThat(verdictOf(material("ML-KEM-768", "public-key", null)).ruleId()).isEqualTo("PQC-STANDARDIZED");

        PqcDecision nameless = verdictOf(material("secret", "private-key", null));
        assertThat(nameless.ruleId()).isEqualTo(PqcRules.FAMILY_UNRESOLVED);
        assertThat(nameless.evaluatedFields())
                .describedAs("an unclassifiable key must be tellable apart from a producer-name gap on an algorithm")
                .containsEntry("assetType", "related-crypto-material")
                .containsEntry("materialType", "private-key");
    }

    /**
     * With no family to subtract, {@code hybridComponents} paired a fragment of the scheme's own name against the
     * scheme, so a plain ML-DSA-65 public key was served "a hybrid construction" beside a classical component that does
     * not exist -- the right verdict and a false statement.
     */
    @Test
    void aPostQuantumPublicKeyIsNotAFabricatedHybrid() {
        for (String name : new String[]{"ML-DSA-65", "SLH-DSA-SHAKE-256f"}) {
            PqcDecision decision = verdictOf(material(name, "public-key", null));
            assertThat(decision.ruleId()).describedAs("name %s", name).isEqualTo("PQC-STANDARDIZED");
            assertThat(decision.evaluatedFields()).doesNotContainKey("hybridComponents");
        }
    }

    /** A key's name may record the hybrid KEX that produced it; a 256-bit session key is still a 256-bit key. */
    @Test
    void aSessionKeyNamedAfterItsHybridKexIsDecidedAsAKey() {
        PqcDecision sessionKey = verdictOf(material("sntrup761x25519-sha512", "shared-secret", 256));
        assertThat(sessionKey.verdict()).isEqualTo(PqcVerdict.READY);
        assertThat(sessionKey.ruleId()).isEqualTo("MATERIAL-SYMMETRIC-READY");
        assertThat(verdictOf(material("X25519-Kyber768", "private-key", null)).ruleId())
                .describedAs("the private key of a hybrid KEM is still decided by its post-quantum half")
                .isEqualTo("PQC-HYBRID-PQC-PRESTANDARD");
    }

    @Test
    void aSymmetricKeyIsReadyOnlyWhenItSaysHowLongItIs() {
        assertThat(verdictOf(material("secret-key@1", "secret-key", 256)).verdict()).isEqualTo(PqcVerdict.READY);
        assertThat(verdictOf(material("secret-key@1", "secret-key", 256)).ruleId())
                .isEqualTo("MATERIAL-SYMMETRIC-READY");

        PqcDecision unsized = verdictOf(material("secret-key@2", "secret-key", null));
        assertThat(unsized.verdict())
                .describedAs("376 of 378 corpus secret keys name no family and 354 state no size; calling those ready "
                        + "would assert a strength nobody reported")
                .isEqualTo(PqcVerdict.UNKNOWN);
        assertThat(verdictOf(material("secret-key@3", "secret-key", 64)).verdict()).isEqualTo(PqcVerdict.NOT_READY);
    }

    /**
     * A stated size below 128 bits is classified by the property it states -- as inadequate -- where an absent one is
     * not; the two used to share one {@code unknown}. Below the ratified size floor bits and bytes cannot be told apart
     * ({@code 32} is AES-256 in bytes), so such a size reads as absent rather than as a strength.
     */
    @Test
    void anUndersizedSymmetricKeyIsNotReadyRatherThanUnknown() {
        PqcDecision weak = verdictOf(material("k", "secret-key", 64));
        assertThat(weak.verdict()).isEqualTo(PqcVerdict.NOT_READY);
        assertThat(weak.ruleId()).isEqualTo("MATERIAL-SYMMETRIC-WEAK");
        assertThat(verdictOf(material("k", "secret-key", 127)).ruleId()).isEqualTo("MATERIAL-SYMMETRIC-WEAK");
        assertThat(verdictOf(material("k", "secret-key", 128)).ruleId()).isEqualTo("MATERIAL-SYMMETRIC-READY");
        for (int outsideTheBand : new int[]{56, 32, 0, -1}) {
            PqcDecision decision = verdictOf(material("k", "secret-key", outsideTheBand));
            assertThat(decision.ruleId())
                    .describedAs("size %s", outsideTheBand)
                    .isEqualTo("MATERIAL-SYMMETRIC-UNSIZED");
            assertThat(decision.evaluatedFields())
                    .describedAs("size %s", outsideTheBand)
                    .doesNotContainKey("materialSize");
        }
    }

    @Test
    void materialThatIsNotAKeyIsNotApplicable() {
        assertThat(verdictOf(material("salt@1", "salt", 128)).ruleId()).isEqualTo("MATERIAL-NOT-KEY");
        assertThat(verdictOf(material("jwt-token", "token", null)).verdict()).isEqualTo(PqcVerdict.NOT_APPLICABLE);
    }

    /**
     * CycloneDX defines {@code key} as material that processes cryptographic data, and the corpus types an
     * {@code RSA-2048 Private Key} that way beside the keystore containers. Calling it not-applicable answered "outside
     * the readiness question" for a private key; unknown is the honest answer for a row with no family.
     */
    @Test
    void aGenericKeyIsNotDeclaredOutsideTheQuestion() {
        assertThat(verdictOf(material("RSA-2048 Private Key", "key", 2048)).verdict())
                .isNotEqualTo(PqcVerdict.NOT_APPLICABLE);
        assertThat(verdictOf(material("truststore.p12", "key", null)).ruleId()).isEqualTo(PqcRules.FAMILY_UNRESOLVED);
    }

    /**
     * The composite case the specification made identity-bearing because dropping the token "silently erases a
     * weak-crypto finding". The family alone says HMAC and CMAC, which are fine; the secondary token says MD5 and 3DES,
     * which are not. The variant column glues its residue to the token list with {@code +}, so the canonical JCE
     * spelling and any spelling with a trailing word used to lose the token and read {@code ready}.
     */
    @Test
    void aBrokenComponentDecidesEvenWhenTheFamilyIsSound() {
        for (String name : new String[]{
                "HMAC-MD5",
                "HMAC-SHA1",
                "PBKDF2-HMAC-SHA1",
                "3DES-CMAC",
                "PBKDF2WithHmacSHA1",
                "AES-RC4 (legacy)",
                "CMAC-DES-EDE3"}) {
            assertThat(verdictOf(algorithm(name)).ruleId())
                    .describedAs("name %s", name)
                    .isEqualTo("CLASSICAL-LEGACY-COMPONENT");
        }
        assertThat(verdictOf(algorithm("AES-CMAC")).ruleId())
                .describedAs("a sound component must not drag a sound family down")
                .isEqualTo("SYMMETRIC-READY");
    }

    /**
     * The other not-ready disposition, from two ratified vectors lifted from a real CBOM: RSA-OAEP wrapping an AES key,
     * and a libsodium sealed box. The elected family is symmetric and the key agreement beside it was discarded, so
     * both read {@code ready} -- one of them beside the producer's own {@code nistQuantumSecurityLevel: 0}.
     */
    @Test
    void aShorBreakableComponentDecidesUnderItsOwnRuleId() {
        for (String name : new String[]{"ECIES-X25519-XSalsa20-Poly1305", "CKM_RSA_AES_KEY_WRAP", "SRP-SHA256"}) {
            PqcDecision decision = verdictOf(algorithm(name));
            assertThat(decision.verdict()).describedAs("name %s", name).isEqualTo(PqcVerdict.NOT_READY);
            assertThat(decision.ruleId()).describedAs("name %s", name).isEqualTo("CLASSICAL-SHOR-COMPONENT");
        }
    }

    /** The not-a-hybrid escape returned the family's verdict directly, so the hash token was never checked. */
    @Test
    void aFalseHybridWithABrokenHashIsNotReady() {
        assertThat(verdictOf(algorithm("ML-KEM-MD5")).ruleId()).isEqualTo("CLASSICAL-LEGACY-COMPONENT");
    }

    /** A family with no grammar rule survives into the variant as its own name; it is the family, not a component. */
    @Test
    void aFamilyWithoutAGrammarRuleIsServedAsTheFamily() {
        for (String name : new String[]{"CMEA", "Yarrow"}) {
            PqcDecision decision = verdictOf(algorithm(name));
            assertThat(decision.ruleId()).describedAs("name %s", name).isEqualTo("CLASSICAL-LEGACY");
            assertThat(decision.evaluatedFields()).containsEntry("algorithmFamily", name);
        }
    }

    /**
     * {@code AssetNormalizer.hybridComponents} tests against its own 25-token set where the tables name 33
     * pseudo-families, so these recorded no components, elected their classical half and read notReady on that half
     * alone -- the one outcome ruling (b) forbids. The ratified tables answer for all 33.
     */
    @Test
    void aHybridTheGrammarMissedIsStillNotDecidedByItsClassicalHalf() {
        for (String name : new String[]{
                "X25519-HAWK-512",
                "ECDH-Raccoon-128",
                "X25519-Picnic",
                "X25519-AIMer-L1",
                "X25519-HAWK (draft)"}) {
            PqcDecision decision = verdictOf(algorithm(name));
            assertThat(decision.ruleId()).describedAs("name %s", name).isEqualTo("PQC-HYBRID-PQC-PRESTANDARD");
            assertThat(decision.evaluatedFields())
                    .describedAs("the components that decided %s", name)
                    .containsKey("hybridComponents");
        }
    }

    /** {@code +} separates the variant's residue from its tokens, and is also the last character of one token. */
    @Test
    void theSphincsPlusTokenSurvivesTheVariantSplit() {
        PqcDecision decision = verdictOf(algorithm("ECDH-SPHINCS+"));
        assertThat(decision.ruleId()).isEqualTo("PQC-HYBRID-PQC-PRESTANDARD");
        assertThat(decision.evaluatedFields()).extracting("hybridComponents").asInstanceOf(LIST).contains("sphincs+");
    }

    /** Among several post-quantum components the answer must not depend on the order the name spelt them. */
    @Test
    void aBrokenPostQuantumComponentIsNotMaskedByAPreStandardOne() {
        assertThat(verdictOf(algorithm("X25519-Kyber768-SIKEp434")).ruleId()).isEqualTo("PQC-HYBRID-PQC-BROKEN");
        assertThat(verdictOf(algorithm("X25519-SIKE-Kyber768")).ruleId()).isEqualTo("PQC-HYBRID-PQC-BROKEN");
        assertThat(verdictOf(algorithm("X25519-ML-KEM-768-SIKE")).ruleId())
                .describedAs("a standardised component wins outright")
                .isEqualTo("PQC-HYBRID-PQC-STANDARDIZED");
    }

    /**
     * core#2196's ruling C10 keeps {@code unknown} as a value of the material-type vocabulary. This rule set is that
     * vocabulary's second reader and takes it as "the producer said nothing", so the row falls through to the family
     * rules rather than becoming a fourth arm of the material partition.
     */
    @Test
    void aMaterialTypeOfUnknownReadsAsNoTypeStated() {
        PqcDecision decision = verdictOf(material("secret-key@4", "unknown", 256));
        assertThat(decision.ruleId())
                .describedAs("a stated `unknown` must not be treated as a symmetric key that happens to declare a "
                        + "size; the producer said nothing, so the material arms do not apply")
                .isNotEqualTo("MATERIAL-SYMMETRIC-READY");
        assertThat(decision.verdict()).isEqualTo(PqcVerdict.UNKNOWN);
    }

    // ---- what the rules genuinely cannot classify
    // --------------------------------------------------------------------

    @Test
    void aNameResolvingToNoRatifiedFamilyIsUnknown() {
        PqcDecision decision = verdictOf(algorithm("Acme Proprietary Wrap"));
        assertThat(decision.verdict()).isEqualTo(PqcVerdict.UNKNOWN);
        assertThat(decision.ruleId()).isEqualTo(PqcRules.FAMILY_UNRESOLVED);
    }

    /**
     * <b>A finding, pinned rather than worked around.</b> FN-DSA is the standardised name for Falcon and is in no
     * ratified table under any spelling, so the asset elects the classical {@code DSA} family and reads its Falcon
     * security level 512 as a key size. The verdict lands on {@code notReady} -- the right answer for the wrong reason,
     * over a wrong stored family and a wrong parameter set.
     *
     * <p>
     * <b>Which ruling closes it, corrected.</b> An earlier revision blamed the grammar's loose form matching inside
     * words and expected core#2196's ruling C7 to fix it. It will not: the rule that fires is the word-guarded
     * {@code (?<![A-Za-z0-9])DSA(?![A-Za-z0-9])}, identical in strict and loose form, and the hyphen in {@code FN-DSA}
     * is a word boundary. C7 guards a preceding <em>letter</em>, and only where a token is contained in a longer family
     * token -- neither holds here. The only repair is a vocabulary act: an FN-DSA pseudo-family in the generator, which
     * is key-affecting and belongs with ruling C12.
     */
    @Test
    void fnDsaElectsTheClassicalDsaFamily() {
        PqcDecision decision = verdictOf(algorithm("FN-DSA-512"));
        assertThat(decision.ruleId())
                .describedAs("if this is now FAMILY-UNRESOLVED or a Falcon disposition, the vocabulary gained FN-DSA "
                        + "and this test should be rewritten to assert the corrected behaviour")
                .isEqualTo("CLASSICAL-SHOR");
        assertThat(decision.evaluatedFields()).containsEntry("algorithmFamily", "DSA");
    }

    @Test
    void anAmbiguousFamilyIsUnknownRatherThanGuessed() {
        PqcDecision decision = verdictOf(algorithm("GOST"));
        assertThat(decision.verdict()).isEqualTo(PqcVerdict.UNKNOWN);
        assertThat(decision.ruleId()).isEqualTo("FAMILY-AMBIGUOUS");
    }

    /**
     * Every GOST curve the tables ratify is a GOST R 34.10 curve, so a curve on the row resolves what the family alone
     * cannot: the row is the EC signature scheme, not Streebog.
     */
    @Test
    void aGostRowWithACurveIsTheEcSignatureScheme() {
        PqcDecision signature = verdictOf(component("algorithm", "gostR3410-2012-256",
                "{\"oid\":\"1.2.643.7.1.1.1.1\",\"algorithmProperties\":{}}"));
        assertThat(signature.ruleId()).isEqualTo("CLASSICAL-SHOR");
        assertThat(signature.evaluatedFields()).containsEntry("curve", "gost/gost256");

        PqcDecision digest = verdictOf(component("algorithm", "gostR3411-2012-256",
                "{\"oid\":\"1.2.643.7.1.1.2.2\",\"algorithmProperties\":{}}"));
        assertThat(digest.ruleId())
                .describedAs("the hash carries no curve and stays ambiguous")
                .isEqualTo("FAMILY-AMBIGUOUS");
    }

    /**
     * SP 800-208 approves LM-OTS only inside LMS; the grammar keeps the discriminator, and the verdict must read it.
     */
    @Test
    void aOneTimeSignatureIsNotReadyOnItsFamilyAlone() {
        for (String name : new String[]{"LM-OTS", "LMOTS_SHA256_N32_W8"}) {
            PqcDecision decision = verdictOf(algorithm(name));
            assertThat(decision.verdict()).describedAs("name %s", name).isEqualTo(PqcVerdict.UNKNOWN);
            assertThat(decision.ruleId()).describedAs("name %s", name).isEqualTo("PQC-ONE-TIME-SIGNATURE");
        }
        assertThat(verdictOf(algorithm("HSS-LMS")).ruleId()).isEqualTo("PQC-STANDARDIZED");
        assertThat(verdictOf(algorithm("XMSS-SHA2_10_256")).ruleId()).isEqualTo("PQC-STANDARDIZED");
    }

    // ---- regressions found by adversarial review ------------------------------------------------------------------

    /**
     * A producer bug stamps {@code relatedCryptoMaterialProperties} onto algorithms; {@code MaterialRedaction} keeps
     * the block whatever the asset type. Ungated, the material arms then decided an algorithm's verdict.
     */
    @Test
    void aStrayMaterialBlockOnAnAlgorithmDoesNotDecideItsVerdict() {
        JsonNode strayed = component("algorithm", "RSA-2048",
                "{\"relatedCryptoMaterialProperties\":{\"type\":\"salt\"}}");
        assertThat(verdictOf(strayed).ruleId())
                .describedAs("the family must still decide; NOT_APPLICABLE here would erase a weak-crypto finding")
                .isEqualTo("CLASSICAL-SHOR");

        JsonNode undersized = component("algorithm", "ML-KEM-768",
                "{\"relatedCryptoMaterialProperties\":{\"type\":\"secret-key\",\"size\":64}}");
        assertThat(verdictOf(undersized).ruleId()).isEqualTo("PQC-STANDARDIZED");
    }

    /**
     * Every FIPS 205 and RFC 8391 parameter-set name carries a hash token, and the normalizer counts any non-PQC
     * secondary token as the classical half -- so the standards spelling of SLH-DSA arrived as a hybrid and was served
     * a hybrid rule id and reason. The verdict was right by luck; the statement was false.
     */
    @Test
    void aStandardsSpelledParameterSetIsNotAHybrid() {
        assertThat(verdictOf(algorithm("SLH-DSA-SHAKE-256f")).ruleId()).isEqualTo("PQC-STANDARDIZED");
        assertThat(verdictOf(algorithm("SLH-DSA-SHA2-128s")).ruleId()).isEqualTo("PQC-STANDARDIZED");
        assertThat(verdictOf(algorithm("XMSSMT-SHA2_20/2_256")).ruleId()).isEqualTo("PQC-STANDARDIZED");
        assertThat(verdictOf(algorithm("SPHINCS+-SHA2-128s")).ruleId()).isEqualTo("PQC-PRESTANDARD");
    }

    /** The ratified detector, not a prefix test: it recognises the OpenSSL and OpenSSH spellings too. */
    @Test
    void theRatifiedCipherSuiteDetectorDecidesSuiteNames() {
        assertThat(verdictOf(algorithm("ECDHE-RSA-AES128-GCM-SHA256")).ruleId()).isEqualTo("NAME-CIPHER-SUITE");
        assertThat(verdictOf(algorithm("DHE-RSA-AES256-SHA")).ruleId()).isEqualTo("NAME-CIPHER-SUITE");
        assertThat(verdictOf(algorithm("TLS-PRF")).ruleId())
                .describedAs("a ratified family that merely starts with TLS- must not be called a suite; that it then "
                        + "resolves to no family is a normalizer gap, not this rule set's")
                .isEqualTo(PqcRules.FAMILY_UNRESOLVED);
    }

    @Test
    void libraryFormatAndCategoryNamesAreNotApplicable() {
        for (String name : new String[]{
                "OpenSSL",
                "BouncyCastle",
                "PKCS#12",
                "PEM",
                "X.509",
                "JWT",
                "block cipher",
                "KEM",
                "MAC"}) {
            assertThat(verdictOf(algorithm(name)).verdict())
                    .describedAs("name %s", name)
                    .isEqualTo(PqcVerdict.NOT_APPLICABLE);
        }
    }

    @Test
    void theCryptanalysedCandidatesAreSeparatedFromTheMerelyUnstandardised() {
        assertThat(verdictOf(algorithm("GeMSS-128")).ruleId()).isEqualTo("PQC-BROKEN");
        assertThat(verdictOf(algorithm("IDEA")).ruleId())
                .describedAs("a 64-bit block cipher belongs with 3DES and Blowfish by this table's own criterion")
                .isEqualTo("CLASSICAL-LEGACY");
        assertThat(verdictOf(algorithm("RIPEMD-128")).ruleId())
                .describedAs("the family spans broken RIPEMD and RIPEMD-160, and no rule reads the size")
                .isEqualTo("FAMILY-AMBIGUOUS");
    }

    /** The mirror of the stray-material case: a producer-declared, resolved family is not discarded on the name. */
    @Test
    void aDeclaredFamilyOutranksACategoryName() {
        PqcDecision declared = verdictOf(component("algorithm", "digest",
                "{\"algorithmProperties\":{\"algorithmFamily\":\"RSA\",\"primitive\":\"signature\"}}"));
        assertThat(declared.ruleId()).isEqualTo("CLASSICAL-SHOR");
        assertThat(declared.evaluatedFields()).containsEntry("algorithmFamily", "RSA");
        assertThat(verdictOf(algorithm("digest")).evaluatedFields()).containsEntry("assetType", "algorithm");
    }

    /**
     * The field that tells {@code HMAC-SHA256} from {@code HMAC-MD5} is the variant, and it was the one field the
     * family verdict did not declare -- while declaring {@code parameterSet}, which no family rule reads.
     */
    @Test
    void aFamilyVerdictDeclaresTheVariantThatCouldHaveOverruledIt() {
        assertThat(verdictOf(algorithm("HMAC-SHA256")).evaluatedFields())
                .containsEntry("variant", "sha-2-256")
                .doesNotContainKey("parameterSet");
    }

    /** The column is NOT NULL with UNROUTABLE in its CHECK, so a null here made the backstop row unwritable. */
    @Test
    void theUnroutableBackstopHasAnAssetType() {
        assertThat(PqcEvaluator.assetTypeOf(null)).isEqualTo(CryptographicAssetType.UNROUTABLE);
        PqcDecision untyped = verdictOf(untyped("Acme Wrap"));
        assertThat(untyped.ruleId()).isEqualTo("ASSET-TYPE-UNROUTABLE");
        assertThat(untyped.evaluatedFields()).containsEntry("assetType", "unroutable");
    }

    @Test
    void aCamelCasedMaterialTypeReachesTheSameArm() {
        assertThat(verdictOf(material("k", "secretKey", 256)).ruleId()).isEqualTo("MATERIAL-SYMMETRIC-READY");
        assertThat(verdictOf(material("k", "SECRET_KEY", 256)).ruleId()).isEqualTo("MATERIAL-SYMMETRIC-READY");
    }

    /** A size that overflows an int truncated to 128 and read as an adequate key. */
    @Test
    void anOutOfRangeMaterialSizeIsAbsentRatherThanTruncated() {
        JsonNode huge = component("related-crypto-material", "k",
                "{\"relatedCryptoMaterialProperties\":{\"type\":\"secret-key\",\"size\":4294967424}}");
        assertThat(verdictOf(huge).ruleId()).isEqualTo("MATERIAL-SYMMETRIC-UNSIZED");
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    private PqcDecision verdictOf(JsonNode component) {
        JsonNode properties = component.get("cryptoProperties");
        return evaluator
                .evaluate(evaluator.fromNormalized(normalizer.normalize(component).asset(), properties),
                        PqcEvaluator.nistQuantumSecurityLevel(properties));
    }

    static JsonNode algorithm(String name) {
        return component("algorithm", name, "{\"algorithmProperties\":{}}");
    }

    static JsonNode material(String name, String type, Integer size) {
        String sizeMember = size == null ? "" : ",\"size\":" + size;
        return component("related-crypto-material", name,
                "{\"relatedCryptoMaterialProperties\":{\"type\":\"" + type + "\"" + sizeMember + "}}");
    }

    static JsonNode component(String assetType, String name, String extraProperties) {
        String properties = extraProperties.equals("{}")
                ? "{\"assetType\":\"" + assetType + "\"}"
                : "{\"assetType\":\"" + assetType + "\"," + extraProperties.substring(1);
        return componentWithProperties(name, properties);
    }

    /** A component naming no asset type at all, which routes to the unroutable backstop. */
    static JsonNode untyped(String name) {
        return componentWithProperties(name, "{}");
    }

    private static JsonNode componentWithProperties(String name, String properties) {
        try {
            return MAPPER
                    .readTree("{\"type\":\"cryptographic-asset\",\"name\":\"" + name + "\",\"cryptoProperties\":"
                            + properties + "}");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
