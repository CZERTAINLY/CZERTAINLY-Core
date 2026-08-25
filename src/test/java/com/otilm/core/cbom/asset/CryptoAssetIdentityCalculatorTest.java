package com.otilm.core.cbom.asset;

import com.otilm.core.model.cbom.CryptographicAssetType;
import java.nio.charset.StandardCharsets;
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
    void normalizeReportsAbsenceForEveryEmptyForm() {
        assertThat(CryptoAssetIdentityCalculator.normalize(null)).isNull();
        assertThat(CryptoAssetIdentityCalculator.normalize("")).isNull();
        assertThat(CryptoAssetIdentityCalculator.normalize("   ")).isNull();
        assertThat(CryptoAssetIdentityCalculator.normalize(" Ed25519 ")).isEqualTo("ed25519");
    }
}
