package com.otilm.core.events.handlers.discovery;

import com.otilm.api.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.UnexpectedRollbackException;

import java.security.cert.CertificateException;

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
                new DataIntegrityViolationException("duplicate key value violates unique constraint")));

        assertThat(reason).isEqualTo("a concurrent import committed the same certificate");
        assertThat(reason).doesNotContain("insert into").doesNotContain("core.certificate");
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
                        + "\"certificate_fingerprint_key\"] [insert into core.certificate (uuid,fingerprint) ...]"));

        assertThat(reason).isEqualTo("a concurrent import committed the same certificate");
        assertThat(reason).doesNotContain("insert into").doesNotContain("certificate_fingerprint_key");
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
