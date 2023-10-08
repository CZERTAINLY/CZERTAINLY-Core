package com.czertainly.core.validation.certificate;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.certificate.CertificateChainResponseDto;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.core.dao.entity.Certificate;

import java.security.cert.CertificateException;

public interface ICertificateValidator {
    CertificateStatus validateCertificate(Certificate certificate, boolean isCompleteChain) throws CertificateException;
}
