package com.czertainly.core.validation.certificate;

import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.core.dao.entity.Certificate;

public interface ICertificateValidator {

    CertificateStatus validateCertificate(Certificate certificate);
}
