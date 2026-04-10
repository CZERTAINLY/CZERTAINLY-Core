package com.czertainly.core.service.tsa.certificatevalidation;

import java.security.cert.X509Certificate;

public sealed interface CertificateValidationResult {

    static CertificateValidationResult valid() {
        return new Valid();
    }

    static CertificateValidationResult invalid(String reason, X509Certificate certificate) {
        return new Invalid(reason, certificate);
    }

    record Valid() implements CertificateValidationResult {}
    record Invalid(String reason, X509Certificate certificate) implements CertificateValidationResult {}
}
