package com.czertainly.core.service.tsa.certificatevalidation;

import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;

@Component
public class SignerCertificateValidationManager {

    // Certificate util
    private final TimestampingEkuValidator ekuValidator = new TimestampingEkuValidator();
    private final QualifiedCertificateValidator qualifiedValidator = new QualifiedCertificateValidator();

    public CertificateValidationResult validate(X509Certificate cert, boolean qualifiedTimestamp) {
        var ekuResult = ekuValidator.validate(cert);
        if (ekuResult instanceof CertificateValidationResult.Invalid) {
            return ekuResult;
        }
        if (qualifiedTimestamp) {
            return qualifiedValidator.validate(cert);
        }
        return ekuResult;
    }
}
