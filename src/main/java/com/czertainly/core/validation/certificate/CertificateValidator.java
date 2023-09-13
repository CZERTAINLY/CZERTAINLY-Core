package com.czertainly.core.validation.certificate;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.core.dao.entity.Certificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CertificateValidator implements ICertificateValidator {
    private static final Logger logger = LoggerFactory.getLogger(CertificateValidator.class);

    public static final String X509 = "X.509";

    /**
     * A map that contains ICertificateValidator implementations mapped to their corresponding certificate type code
     */
    private final Map<String, ICertificateValidator> certificateValidatorMap;

    /**
     * Uses constructor-autowiring to create a new CertificateValidator instance
     * with the provided map of ICertificateValidator implementations.
     *
     * @param certificateValidatorMap a map of ICertificateValidator implementations
     */
    public CertificateValidator(Map<String, ICertificateValidator> certificateValidatorMap) {
        // validate keys
        for (String certificateType: certificateValidatorMap.keySet()) {
            try {
                CertificateType.fromCode(certificateType);
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Unknown certificate type in CertificateValidator: " + certificateType);
            }
        }
        this.certificateValidatorMap = certificateValidatorMap;
    }

    @Override
    public CertificateStatus validateCertificate(Certificate certificate) {
        ICertificateValidator certificateValidator = getCertificateValidator(certificate.getCertificateType());
        return certificateValidator.validateCertificate(certificate);
    }

    private ICertificateValidator getCertificateValidator(CertificateType certificateType) {
        ICertificateValidator certificateValidator = certificateValidatorMap.get(certificateType.getCode());
        if (certificateValidator == null) {
            throw new ValidationException("Unsupported certificate type validator for certificate type " + certificateType.getLabel());
        }
        return certificateValidator;
    }
}
