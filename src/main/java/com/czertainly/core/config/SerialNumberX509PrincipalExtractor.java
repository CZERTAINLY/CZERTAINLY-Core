package com.czertainly.core.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.web.authentication.preauth.x509.X509PrincipalExtractor;

import java.security.cert.X509Certificate;

public class SerialNumberX509PrincipalExtractor implements X509PrincipalExtractor {
    protected final Log logger = LogFactory.getLog(this.getClass());

    public SerialNumberX509PrincipalExtractor() {
    }

    public Object extractPrincipal(X509Certificate clientCert) {
        String serialNumber = clientCert.getSerialNumber().toString(16);
        this.logger.debug("Serial Number is '" + serialNumber + "'");
        return serialNumber;
    }
}