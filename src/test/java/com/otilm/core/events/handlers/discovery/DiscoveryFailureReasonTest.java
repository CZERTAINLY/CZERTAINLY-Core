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
    void passesThroughACertificateExceptionMessage() {
        assertThat(DiscoveryFailureReason.shape(new CertificateException("unsupported signature algorithm")))
                .isEqualTo("unsupported signature algorithm");
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
    void neverReturnsNullForAMessagelessControlledException() {
        assertThat(DiscoveryFailureReason.shape(new CertificateException()))
                .isEqualTo("an unexpected error occurred");
    }
}
