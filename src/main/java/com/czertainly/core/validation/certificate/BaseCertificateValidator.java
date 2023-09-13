package com.czertainly.core.validation.certificate;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.core.dao.entity.Certificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

public abstract class BaseCertificateValidator implements ICertificateValidator {
    private static final Logger logger = LoggerFactory.getLogger(BaseCertificateValidator.class);

}
