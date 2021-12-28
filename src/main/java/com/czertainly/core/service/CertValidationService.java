package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.core.dao.entity.Certificate;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.List;

public interface CertValidationService {
    void validateAllCertificates();

    void validateCertificates(List<Certificate> certificates);

    void validate(Certificate certificate) throws NotFoundException, CertificateException, IOException;
}
