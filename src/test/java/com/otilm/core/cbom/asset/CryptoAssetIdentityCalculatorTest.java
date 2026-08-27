package com.otilm.core.cbom.asset;

import com.otilm.api.model.core.cryptoasset.CryptographicAssetType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoAssetIdentityCalculatorTest {

    private static CryptoAssetIdentityFields algorithm(String name, String parameterSet, String curve) {
        return new CryptoAssetIdentityFields(CryptographicAssetType.ALGORITHM, name, null, null, null, parameterSet,
                curve, null, null, null);
    }

    @Test
    void theSameFieldsAlwaysKeyToTheSameValue() {
        assertThat(CryptoAssetIdentityCalculator.calculate(algorithm("RSA", "2048", null)))
                .isEqualTo(CryptoAssetIdentityCalculator.calculate(algorithm("RSA", "2048", null)))
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void differentFieldsKeyDifferently() {
        assertThat(CryptoAssetIdentityCalculator.calculate(algorithm("RSA", "2048", null)))
                .isNotEqualTo(CryptoAssetIdentityCalculator.calculate(algorithm("RSA", "4096", null)));
    }

    @Test
    void theAssetTypeIsPartOfTheIdentity() {
        CryptoAssetIdentityFields asAlgorithm = new CryptoAssetIdentityFields(CryptographicAssetType.ALGORITHM, "RSA",
                null, null, null, null, null, null, null, null);
        CryptoAssetIdentityFields asCertificate = new CryptoAssetIdentityFields(CryptographicAssetType.CERTIFICATE,
                "RSA", null, null, null, null, null, null, null, null);

        assertThat(CryptoAssetIdentityCalculator.calculate(asAlgorithm))
                .isNotEqualTo(CryptoAssetIdentityCalculator.calculate(asCertificate));
    }

    @Test
    void caseFoldingDoesNotDependOnThePlatformLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            String turkish = CryptoAssetIdentityCalculator.calculate(algorithm("KYBER-I", null, null));
            Locale.setDefault(Locale.US);
            String english = CryptoAssetIdentityCalculator.calculate(algorithm("KYBER-I", null, null));

            assertThat(turkish)
                    .describedAs("A Turkish-locale JVM folds 'I' to a dotless i; two nodes of one cluster must not "
                            + "key the same asset differently")
                    .isEqualTo(english);
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void caseDoesNotChangeTheIdentity() {
        assertThat(CryptoAssetIdentityCalculator.calculate(algorithm("rsa", null, null)))
                .isEqualTo(CryptoAssetIdentityCalculator.calculate(algorithm("RSA", null, null)));
    }

    @Test
    void unicodeSpellingsOfOneIdentifierAgree() {
        String fullwidth = "\uff32\uff33\uff21";
        assertThat(fullwidth)
                .describedAs("the fullwidth spelling must really differ from the ASCII one")
                .isNotEqualTo("RSA");

        assertThat(CryptoAssetIdentityCalculator.calculate(algorithm(fullwidth, null, null)))
                .isEqualTo(CryptoAssetIdentityCalculator.calculate(algorithm("RSA", null, null)));
    }

    /**
     * The fullwidth case above passes under either ordering, because fullwidth letters do have case mappings. A
     * compatibility character that has none is what separates the two: fold-then-normalise leaves
     * {@code U+1D400 MATHEMATICAL BOLD CAPITAL A} alone and NFKC then yields {@code A}, so a case-folding normaliser
     * returns an uppercase result and the two spellings key apart.
     */
    @Test
    void aCompatibilityCharacterWithNoCaseMappingStillFolds() {
        String mathematicalBold = "𝐀𝐄𝐒";
        assertThat(mathematicalBold.toLowerCase(Locale.ROOT))
                .describedAs("these characters have no lowercase mapping, which is the whole point of the case")
                .isEqualTo(mathematicalBold);

        assertThat(CryptoAssetIdentityCalculator.calculate(algorithm(mathematicalBold, null, null)))
                .isEqualTo(CryptoAssetIdentityCalculator.calculate(algorithm("AES", null, null)));
    }

    /**
     * The converse ordering hazard: a full case mapping can emit a sequence that is not itself normalised, so the
     * pipeline normalises again after folding. {@code U+0130 LATIN CAPITAL LETTER I WITH DOT ABOVE} folds to {@code i}
     * plus {@code U+0307 COMBINING DOT ABOVE}, and the composed and already-decomposed spellings must land together.
     *
     * <p>
     * They land on {@code i + U+0307}, not on plain {@code i} — which is correct, and is what Unicode's own
     * {@code NFKC_Casefold} does with this character. The assertion below is the property that matters; asserting
     * {@code İ} equals {@code i} would be asserting a folding Unicode does not perform.
     */
    @Test
    void aFoldThatReDecomposesIsNormalisedAgain() {
        String composedDottedCapitalI = "İV";
        String foldedSpelling = "i̇v";
        assertThat(composedDottedCapitalI.toLowerCase(Locale.ROOT))
                .describedAs("the fold really does emit a combining sequence, or this test proves nothing")
                .isEqualTo(foldedSpelling);

        assertThat(CryptoAssetIdentityCalculator.calculate(algorithm(composedDottedCapitalI, null, null)))
                .isEqualTo(CryptoAssetIdentityCalculator.calculate(algorithm(foldedSpelling, null, null)));
    }

    /**
     * Unicode space must not survive into the identity. {@code trim()} removes only characters at or below
     * {@code U+0020}, so a trailing {@code U+00A0 NO-BREAK SPACE} used to survive it and NFKC then turned it into an
     * ordinary trailing space -- keying the same identifier twice.
     */
    @Test
    void unicodeWhitespaceIsNotIdentity() {
        assertThat(CryptoAssetIdentityCalculator.normalize("RSA\u00a0"))
                .describedAs("a no-break space must not become a trailing ASCII space")
                .isEqualTo("rsa");

        assertThat(CryptoAssetIdentityCalculator.calculate(algorithm("RSA\u00a0", null, null)))
                .isEqualTo(CryptoAssetIdentityCalculator.calculate(algorithm("RSA", null, null)));
    }

    /** A field of nothing but Unicode space is absent, exactly as a field of ASCII spaces is. */
    @Test
    void aFieldOfOnlyUnicodeSpaceIsAbsent() {
        assertThat(CryptoAssetIdentityCalculator.normalize("\u3000")).isNull();

        assertThat(CryptoAssetIdentityCalculator.calculate(algorithm("RSA", "\u3000", null)))
                .isEqualTo(CryptoAssetIdentityCalculator.calculate(algorithm("RSA", null, null)));
    }

    @Test
    void decomposedAndComposedFormsAgree() {
        String composed = "caf\u00e9";
        String decomposed = "cafe\u0301";
        assertThat(composed)
                .describedAs("the two spellings must really differ, or this test proves nothing")
                .isNotEqualTo(decomposed);

        assertThat(CryptoAssetIdentityCalculator.calculate(algorithm(composed, null, null)))
                .isEqualTo(CryptoAssetIdentityCalculator.calculate(algorithm(decomposed, null, null)));
    }

    @Test
    void surroundingWhitespaceIsNotIdentity() {
        assertThat(CryptoAssetIdentityCalculator.calculate(algorithm("  RSA \t", null, null)))
                .isEqualTo(CryptoAssetIdentityCalculator.calculate(algorithm("RSA", null, null)));
    }

    @Test
    void aBlankFieldMeansTheSameAsAnAbsentOne() {
        assertThat(CryptoAssetIdentityCalculator.calculate(algorithm("RSA", "", null)))
                .describedAs("producers omit a field and emit an empty string interchangeably")
                .isEqualTo(CryptoAssetIdentityCalculator.calculate(algorithm("RSA", null, null)));
    }

    @Test
    void aFieldValueCannotForgeAFieldBoundary() {
        // Without length framing, "2048" in parameterSet and a curve of "p256" could be spelled into one field and
        // reproduce the other layout.
        String separatorAttack = CryptoAssetIdentityCalculator.calculate(algorithm("RSA", "2048:p256", null));
        String genuine = CryptoAssetIdentityCalculator.calculate(algorithm("RSA", "2048", "p256"));

        assertThat(separatorAttack).isNotEqualTo(genuine);
    }

    @Test
    void movingContentBetweenAdjacentFieldsChangesTheIdentity() {
        assertThat(CryptoAssetIdentityCalculator.calculate(algorithm("RSA2048", null, null)))
                .isNotEqualTo(CryptoAssetIdentityCalculator.calculate(algorithm("RSA", "2048", null)));
    }

    @Test
    void thePreimageIsLengthFramedAndDomainSeparated() {
        String preimage = new String(CryptoAssetIdentityCalculator.preimage(algorithm("RSA", null, null)),
                StandardCharsets.UTF_8);

        assertThat(preimage)
                .startsWith("25:otilm:cbom-asset-identity")
                .contains("9:ALGORITHM")
                .contains("3:rsa")
                .endsWith("--------")
                .describedAs("absent fields are encoded as a byte that is not a valid length")
                .contains("-");
    }

    @Test
    void anAbsentFieldIsDistinctFromEveryPresentValue() {
        String withDash = new String(CryptoAssetIdentityCalculator.preimage(algorithm("-", null, null)),
                StandardCharsets.UTF_8);

        assertThat(withDash)
                .describedAs("a literal '-' is framed with its length, so it cannot be read as the absent marker")
                .contains("1:-");
        assertThat(CryptoAssetIdentityCalculator.calculate(algorithm("-", null, null)))
                .isNotEqualTo(CryptoAssetIdentityCalculator.calculate(algorithm(null, null, null)));
    }

    @Test
    void anAssetWithNoTypeStillKeys() {
        CryptoAssetIdentityFields untyped = new CryptoAssetIdentityFields(null, "RSA", null, null, null, null, null,
                null, null, null);

        assertThat(CryptoAssetIdentityCalculator.calculate(untyped))
                .describedAs("the column is NOT NULL, but a keying function that threw would fail ingest on a shape "
                        + "this version has not met")
                .hasSize(64)
                .isNotEqualTo(CryptoAssetIdentityCalculator.calculate(algorithm("RSA", null, null)));
    }

    @Test
    void normalizeReportsAbsenceForEveryEmptyForm() {
        assertThat(CryptoAssetIdentityCalculator.normalize(null)).isNull();
        assertThat(CryptoAssetIdentityCalculator.normalize("")).isNull();
        assertThat(CryptoAssetIdentityCalculator.normalize("   ")).isNull();
        assertThat(CryptoAssetIdentityCalculator.normalize(" Ed25519 ")).isEqualTo("ed25519");
    }

    /**
     * The known-answer vector. Every other test here is a relation -- these two agree, those two differ -- and a
     * relation cannot see a change that moves every key at once. Swap the {@code parameterSet} and {@code curve} frames
     * in the calculator and the rest of this class still passes, because no other test populates both; every stored row
     * carrying either field would then silently re-key on its next sync, with no build failure to say so.
     *
     * <p>
     * All ten fields are populated, so the vector covers the whole preimage and its field order. If this constant has
     * to change, the preimage changed: that is a re-keying event, and
     * {@link CryptoAssetIdentityCalculator#RULESET_VERSION} must be bumped in the same commit so existing rows stay
     * findable by {@code findUuidsKeyedBefore}.
     */
    /**
     * The property that makes the stored row sufficient to re-derive its own key. The staleness sweep re-keys rows
     * whose rule-set version has fallen behind, and it can only read the columns -- so normalizing must be a fixed
     * point, or a row keyed from raw input and re-keyed from its stored columns would land on two different keys and
     * split the inventory silently.
     */
    @Test
    void normalizingIsAFixedPointAndDoesNotMoveTheKey() {
        List<CryptoAssetIdentityFields> vectors = List
                .of(new CryptoAssetIdentityFields(CryptographicAssetType.ALGORITHM, "  ECDSA  ", "\u3000",
                        "\uFF25\uFF23\uFF24\uFF33\uFF21", "SIGNATURE", " P-256 ", null, "CBC", "", "FIPS186-4"),
                        new CryptoAssetIdentityFields(CryptographicAssetType.CERTIFICATE, "\u0130stanbul", null, null,
                                null, null, null, null, null, null),
                        new CryptoAssetIdentityFields(CryptographicAssetType.PROTOCOL, "\u00A0TLS\u00A0", "1.3", null,
                                null, null, null, null, null, null),
                        new CryptoAssetIdentityFields(null, null, null, null, null, null, null, null, null, null));

        for (CryptoAssetIdentityFields raw : vectors) {
            CryptoAssetIdentityFields stored = raw.normalized();
            assertThat(CryptoAssetIdentityCalculator.calculate(stored))
                    .describedAs("normalizing the input must not move the key: %s", raw)
                    .isEqualTo(CryptoAssetIdentityCalculator.calculate(raw));
            assertThat(stored.normalized())
                    .describedAs("normalizing twice must equal normalizing once: %s", raw)
                    .isEqualTo(stored);
        }
    }

    @Test
    void normalizingFoldsEveryBlankFormToAbsent() {
        CryptoAssetIdentityFields blanks = new CryptoAssetIdentityFields(CryptographicAssetType.ALGORITHM, "", "  ",
                "\u3000", "\u00A0", "\t", null, " ", "\n", "\u2007").normalized();

        assertThat(blanks.name()).isNull();
        assertThat(blanks.oid()).isNull();
        assertThat(blanks.algorithmFamily()).isNull();
        assertThat(blanks.primitive()).isNull();
        assertThat(blanks.parameterSet()).isNull();
        assertThat(blanks.mode()).isNull();
        assertThat(blanks.padding()).isNull();
        assertThat(blanks.variant()).isNull();
        assertThat(blanks.assetType())
                .describedAs("the platform's own enum constant is already canonical and passes through")
                .isEqualTo(CryptographicAssetType.ALGORITHM);
    }

    @Test
    void theIdentityKeyMatchesItsKnownAnswerVector() {
        CryptoAssetIdentityFields allFieldsPopulated = new CryptoAssetIdentityFields(CryptographicAssetType.ALGORITHM,
                "ECDSA", "1.2.840.10045.4.3.2", "ecdsa", "signature", "P-256", "secp256r1", "cbc", "pkcs1v15",
                "fips186-4");

        assertThat(CryptoAssetIdentityCalculator.calculate(allFieldsPopulated))
                .describedAs("changing this constant means the preimage moved; bump RULESET_VERSION in the same commit")
                .isEqualTo("e6f06439413a41cfae619d72f487063fd5a7a17bd127d4efb531495d79055a36");
        assertThat(CryptoAssetIdentityCalculator.RULESET_VERSION)
                .describedAs("the vector above was computed under this rule-set generation")
                .isEqualTo(1);
    }

    /**
     * The vector above populates every field but gives {@code name} and {@code algorithmFamily} the same value, so
     * swapping those two frames leaves its preimage byte-identical and it cannot detect that swap. This one gives all
     * ten fields distinct normalized values, so any transposition of any two frames moves the digest.
     *
     * <p>
     * Both are pinned rather than one replacing the other: the vector above is the historical constant, and a change to
     * it means the framing rules moved and every stored row re-keys. A change to this one means the same thing unless
     * its <em>inputs</em> above changed with it.
     *
     * <p>
     * Computed outside the JVM, so it pins the documented framing rather than echoing this implementation:
     *
     * <pre>
     * printf '25:otilm:cbom-asset-identity9:ALGORITHM5:ecdsa19:1.2.840.10045.4.3.214:elliptic-curve\
     * 9:signature5:p-2569:secp256r13:cbc8:pkcs1v159:fips186-4' | sha256sum
     * </pre>
     */
    @Test
    void everyFrameIsPositionallyPinnedByADistinctValueVector() {
        CryptoAssetIdentityFields allFieldsDistinct = new CryptoAssetIdentityFields(CryptographicAssetType.ALGORITHM,
                "ECDSA", "1.2.840.10045.4.3.2", "elliptic-curve", "signature", "P-256", "secp256r1", "cbc", "pkcs1v15",
                "fips186-4");

        assertThat(CryptoAssetIdentityCalculator.calculate(allFieldsDistinct))
                .describedAs("changing this constant means the preimage moved; bump RULESET_VERSION in the same commit")
                .isEqualTo("1867d1a17e10c7a403c1e8a4ebd9cb7593e48c4062a24b0875238cd426bf2e99");
    }
}
