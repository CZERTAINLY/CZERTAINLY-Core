package com.czertainly.core.service;

import com.czertainly.core.dao.entity.Crl;

import java.io.IOException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.UUID;

public interface CrlService {

    Crl createCrlAndCrlEntries(byte[] crlDistributionPointsEncoded, String issuerDn, String issuerSerialNumber, UUID caCertificateUuid) throws IOException;

    Crl createDeltaCrlAndCrlEntries(X509Certificate certificate, Crl crl, String issuerDn, String issuerSerialNumber, UUID caCertificateUuid) throws IOException;

    Crl getCurrentCrl(X509Certificate certificate, X509Certificate issuerCertificate) throws IOException;
}
