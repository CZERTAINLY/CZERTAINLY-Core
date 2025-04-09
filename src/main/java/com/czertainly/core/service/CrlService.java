package com.czertainly.core.service;

import com.czertainly.core.dao.entity.Crl;
import com.czertainly.core.dao.entity.CrlEntry;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.UUID;

public interface CrlService {

    UUID getCurrentCrl(X509Certificate certificate, X509Certificate issuerCertificate) throws IOException;

    CrlEntry findCrlEntryForCertificate(String serialNumber, UUID crlUuid);

    List<Crl> findCrlsForCaCertificate(UUID caCertificateUuid);
}
