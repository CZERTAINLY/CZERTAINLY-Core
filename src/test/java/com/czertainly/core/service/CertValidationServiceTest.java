package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.certificate.*;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.MetaDefinitions;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

@SpringBootTest
@Transactional
@Rollback
public class CertValidationServiceTest extends BaseSpringBootTest {

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;

    private Certificate certificate;
    private CertificateContent certificateContent;

    private X509Certificate x509Cert;

    @BeforeEach
    public void setUp() throws GeneralSecurityException, IOException {
        InputStream keyStoreStream = CertificateServiceTest.class.getClassLoader().getResourceAsStream("client1.p12");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(keyStoreStream, "123456".toCharArray());

        x509Cert = (X509Certificate) keyStore.getCertificate("1");

        certificateContent = new CertificateContent();
        certificateContent.setContent(Base64.getEncoder().encodeToString(x509Cert.getEncoded()));
        certificateContent = certificateContentRepository.save(certificateContent);

        certificate = new Certificate();
        certificate.setSubjectDn("testCertificate");
        certificate.setIssuerDn("testCertificate");
        certificate.setSerialNumber("123456789");
        certificate.setStatus(CertificateStatus.VALID);
        certificate.setCertificateType(CertificateType.X509);
        certificate.setNotBefore(new Date());
        certificate.setNotAfter(new Date());
        certificate.setCertificateContent(certificateContent);
        certificate = certificateRepository.save(certificate);
    }

    @Test
    public void testValidateCertificate() throws CertificateException {
        certificateService.validate(certificate);

        String result = certificate.getCertificateValidationResult();
        Assertions.assertNotNull(result);
        Assertions.assertTrue(StringUtils.isNotBlank(result));

        Map<CertificateValidationCheck, CertificateValidationDto> resultMap = MetaDefinitions.deserializeValidation(result);
        Assertions.assertNotNull(resultMap);
        Assertions.assertFalse(resultMap.isEmpty());

        CertificateValidationDto signatureVerification = resultMap.get(CertificateValidationCheck.CERTIFICATE_CHAIN);
        Assertions.assertNotNull(signatureVerification);
        Assertions.assertEquals(CertificateValidationStatus.FAILED, signatureVerification.getStatus());
    }
}
