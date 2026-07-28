package com.otilm.core.events.handlers.discovery;

import com.otilm.api.exception.ValidationException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.UnexpectedRollbackException;

import java.security.cert.CertificateException;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryFailureReasonTest {

    @Test
    void passesThroughAControlledDomainMessage() {
        assertThat(DiscoveryFailureReason.shape(
                new ValidationException("the certificate has no subject alternative names")))
                .isEqualTo("the certificate has no subject alternative names");
    }

    @Test
    void classifiesACertificateExceptionRatherThanForwardingProviderText() {
        assertThat(DiscoveryFailureReason.shape(new CertificateException("unsupported signature algorithm")))
                .as("parser messages come from the JDK and BouncyCastle, not the platform")
                .isEqualTo("the discovered certificate could not be parsed");
    }

    @Test
    void classifiesAWrappedDataIntegrityViolationWithoutLeakingTheWrappersText() {
        String reason = DiscoveryFailureReason.shape(new IllegalStateException(
                "could not execute statement [insert into core.certificate (uuid,fingerprint) ...]",
                new DataIntegrityViolationException("duplicate key value violates unique constraint",
                        uniqueViolation("certificate_fingerprint_key"))));

        assertThat(reason).isEqualTo("a concurrent import committed the same certificate");
        assertThat(reason).doesNotContain("insert into").doesNotContain("core.certificate");
    }

    @Test
    void separatesADuplicateFromAnyOtherIntegrityViolation() {
        assertThat(DiscoveryFailureReason.shape(new DataIntegrityViolationException(
                "violates foreign key constraint", constraintViolation(
                        ConstraintViolationException.ConstraintKind.OTHER, "certificate_owner_fk"))))
                .as("a foreign-key or check violation is a different defect and must not read as a benign race")
                .isEqualTo("a database constraint rejected the certificate");
    }

    @Test
    void passesThroughAnAlreadyShapedRollbackReason() {
        String reason = DiscoveryFailureReason.shape(new DiscoveryImportRollbackException(
                "trigger evaluation failed: the discovered certificate could not be parsed",
                new DataIntegrityViolationException("insert into core.certificate ...")));

        assertThat(reason)
                .as("re-deriving from the cause would replace the specific reason with generic text")
                .isEqualTo("trigger evaluation failed: the discovered certificate could not be parsed");
    }

    @Test
    void survivesACyclicCauseChain() {
        RuntimeException outer = new RuntimeException("outer");
        RuntimeException inner = new RuntimeException("inner", outer);
        outer.initCause(inner);

        assertThat(DiscoveryFailureReason.shape(outer)).isEqualTo("an unexpected error occurred");
    }

    @Test
    void classifiesADataIntegrityViolationWithoutLeakingSql() {
        String reason = DiscoveryFailureReason.shape(new DataIntegrityViolationException(
                "could not execute statement [ERROR: duplicate key value violates unique constraint "
                        + "\"certificate_fingerprint_key\"] [insert into core.certificate (uuid,fingerprint) ...]",
                uniqueViolation("certificate_fingerprint_key")));

        assertThat(reason).isEqualTo("a concurrent import committed the same certificate");
        assertThat(reason).doesNotContain("insert into").doesNotContain("certificate_fingerprint_key");
    }

    /**
     * Without a Hibernate constraint violation in the chain the kind is unknowable, so the reason has to stay at the
     * weaker claim rather than assert a race that may not have happened.
     */
    @Test
    void doesNotClaimARaceWhenTheConstraintKindIsUnknown() {
        assertThat(DiscoveryFailureReason.shape(
                new DataIntegrityViolationException("could not execute statement")))
                .isEqualTo("a database constraint rejected the certificate");
    }

    private static ConstraintViolationException uniqueViolation(String constraintName) {
        return constraintViolation(ConstraintViolationException.ConstraintKind.UNIQUE, constraintName);
    }

    private static ConstraintViolationException constraintViolation(
            ConstraintViolationException.ConstraintKind kind, String constraintName) {
        return new ConstraintViolationException("could not execute statement",
                new SQLException("ERROR: violates constraint \"%s\"".formatted(constraintName)),
                kind, constraintName);
    }

    @Test
    void classifiesAnUnexpectedRollbackWithoutLeakingInternals() {
        assertThat(DiscoveryFailureReason.shape(new UnexpectedRollbackException(
                "Transaction silently rolled back because it has been marked as rollback-only")))
                .isEqualTo("the import transaction was rolled back");
    }

    @Test
    void classifiesAnythingElseGenerically() {
        assertThat(DiscoveryFailureReason.shape(new IllegalStateException("core.certificate_content.id is null")))
                .isEqualTo("an unexpected error occurred");
    }

    @Test
    void neverEchoesAStringifiedNullMessage() {
        assertThat(DiscoveryFailureReason.shape(new ValidationException((String) null)))
                .isEqualTo("an unexpected error occurred");
    }
}
