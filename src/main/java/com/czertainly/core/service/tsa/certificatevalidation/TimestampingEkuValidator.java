package com.czertainly.core.service.tsa.certificatevalidation;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.List;

final class TimestampingEkuValidator {

    private static final String TIMESTAMPING_EKU = "1.3.6.1.5.5.7.3.8";

    CertificateValidationResult validate(X509Certificate cert) {
        try {
            List<String> ekus = cert.getExtendedKeyUsage();
            if (ekus == null || !ekus.contains(TIMESTAMPING_EKU)) {
                return CertificateValidationResult.invalid(
                        "Signer certificate does not have timestamping EKU (%s)".formatted(TIMESTAMPING_EKU), cert);
            }
            return CertificateValidationResult.valid();
        } catch (CertificateParsingException e) {
            throw new RuntimeException("Failed to parse EKU from signer certificate", e);
        }
    }
}
