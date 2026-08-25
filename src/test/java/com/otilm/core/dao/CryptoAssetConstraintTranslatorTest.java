package com.otilm.core.dao;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fence over the exception-translation sites this ticket adds.
 *
 * <p>
 * PostgreSQL reports a constraint violation with a {@code DETAIL} line that quotes the failing row -- for
 * {@code crypto_asset} that row carries the identity key, whose whole protection is that it never leaves the database.
 * Each test here builds a failure whose message carries that DETAIL and asserts the translated text carries none of it.
 */
class CryptoAssetConstraintTranslatorTest {

    /**
     * A realistic driver message. The identity key, the table names and the SQL are all things a caller must never be
     * handed.
     */
    private static final String DRIVER_MESSAGE = """
            ERROR: update or delete on table "cbom" violates foreign key constraint \
            "crypto_asset_source_to_cbom_key" on table "crypto_asset_source"
              Detail: Key (uuid)=(2f8c0f6e-0000-4000-8000-000000000001) is still referenced from table \
            "crypto_asset_source".
              Where: SQL statement "DELETE FROM core.cbom WHERE uuid = $1"
            """;

    private static DataIntegrityViolationException failure(String constraintName) {
        ConstraintViolationException hibernateFailure = new ConstraintViolationException(DRIVER_MESSAGE,
                new SQLException(DRIVER_MESSAGE, "23503"), constraintName);
        return new DataIntegrityViolationException(DRIVER_MESSAGE, hibernateFailure);
    }

    @Test
    void aRestrictedCbomDeleteIsExplainedWithoutTheDriverText() {
        String description = CryptoAssetConstraintTranslator.describe(failure("crypto_asset_source_to_cbom_key"));

        assertThat(description)
                .isEqualTo("The CBOM is still referenced by the cryptographic asset inventory. "
                        + "Its asset sources must be detached before the CBOM can be deleted.");
        assertThat(description)
                .doesNotContain("Detail")
                .doesNotContain("DETAIL")
                .doesNotContain("2f8c0f6e")
                .doesNotContain("DELETE FROM")
                .doesNotContain("$1");
    }

    @Test
    void everyKnownConstraintIsExplainedWithoutTheDriverText() {
        for (String constraint : new String[]{
                "uq_crypto_asset_identity_key",
                "uq_crypto_asset_source",
                "uq_crypto_asset_alias_absorbed",
                "crypto_asset_alias_to_canonical_key",
                "ck_crypto_asset_alias_not_self",
                "crypto_asset_to_properties_source_key",
                "uq_cbom_tombstone_serial_version",
                "ck_crypto_asset_properties_pair"}) {
            String description = CryptoAssetConstraintTranslator.describe(failure(constraint));

            assertThat(description)
                    .describedAs("constraint %s", constraint)
                    .isNotEqualTo(CryptoAssetConstraintTranslator.GENERIC_REJECTION)
                    .doesNotContain("Detail")
                    .doesNotContain("2f8c0f6e")
                    .doesNotContain("DELETE FROM");
        }
    }

    @Test
    void anUnknownConstraintFallsBackToAFixedSentence() {
        assertThat(CryptoAssetConstraintTranslator.describe(failure("some_constraint_added_later")))
                .isEqualTo(CryptoAssetConstraintTranslator.GENERIC_REJECTION);
    }

    @Test
    void aFailureCarryingNoConstraintNameFallsBackToAFixedSentence() {
        assertThat(CryptoAssetConstraintTranslator
                .describe(new DataIntegrityViolationException(DRIVER_MESSAGE, new SQLException(DRIVER_MESSAGE))))
                .isEqualTo(CryptoAssetConstraintTranslator.GENERIC_REJECTION);
        assertThat(CryptoAssetConstraintTranslator.describe(null))
                .isEqualTo(CryptoAssetConstraintTranslator.GENERIC_REJECTION);
    }

    @Test
    void aSchemaQualifiedAndUppercasedConstraintNameStillResolves() {
        assertThat(CryptoAssetConstraintTranslator.describe(failure("core.UQ_CRYPTO_ASSET_SOURCE")))
                .isEqualTo("This CBOM's contribution to the cryptographic asset is already recorded.");
    }

    @Test
    void theConstraintNameIsTakenFromTheCauseChainNotTheMessage() {
        assertThat(CryptoAssetConstraintTranslator.constraintNameOf(failure("uq_crypto_asset_source")))
                .contains("uq_crypto_asset_source");
        assertThat(CryptoAssetConstraintTranslator
                .constraintNameOf(new IllegalStateException("uq_crypto_asset_source appears only in this text")))
                .describedAs("a constraint name that appears only in a message must not be trusted")
                .isEmpty();
    }
}
