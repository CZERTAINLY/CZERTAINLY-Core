package com.czertainly.core.service;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.List;

import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.api.exception.NotFoundException;

public interface CertValidationService {
    void validateAllCertificates();

    void validateCertificates(List<Certificate> certificates);

    void validate(Certificate certificate) throws NotFoundException, CertificateException, IOException;
}
