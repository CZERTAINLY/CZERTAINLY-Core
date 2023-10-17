package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.certificate.*;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.MetaDefinitions;
import com.czertainly.core.util.OcspUtil;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@Transactional
@Rollback
public class CertificateValidationTest extends BaseSpringBootTest {

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;

    private Certificate certificate;
    private CertificateContent certificateContent;

    private Certificate caCertificate;

    private X509Certificate x509Cert;

    private Certificate chainIncompleteCertificate;

    private Certificate chainCompleteCertificate;

    private WireMockServer mockServer;

    @BeforeEach
    public void setUp() throws GeneralSecurityException, IOException, com.czertainly.api.exception.CertificateException {

        mockServer = new WireMockServer(80);
        mockServer.start();
        WireMock.configureFor("http://x1.i.lencr.org/", mockServer.port());

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

        String caCertificateContent = "MIIFyzCCA7OgAwIBAgIUOymQEJz0e6+2cDE/" + "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";

        caCertificate = certificateService.createCertificate(caCertificateContent, CertificateType.X509);

        String chainCertificateContent = "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";
        chainIncompleteCertificate = certificateService.createCertificate(chainCertificateContent, CertificateType.X509);

        String chainCompleteCertificateContent = "MIIFFjCCAv6gAwIBAgIRAJErCErPDBinU/bWLiWnX1owDQYJKoZIhvcNAQELBQAwTzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2VhcmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMjAwOTA0MDAwMDAwWhcNMjUwOTE1MTYwMDAwWjAyMQswCQYDVQQGEwJVUzEWMBQGA1UEChMNTGV0J3MgRW5jcnlwdDELMAkGA1UEAxMCUjMwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC7AhUozPaglNMPEuyNVZLD+ILxmaZ6QoinXSaqtSu5xUyxr45r+XXIo9cPR5QUVTVXjJ6oojkZ9YI8QqlObvU7wy7bjcCwXPNZOOftz2nwWgsbvsCUJCWH+jdxsxPnHKzhm+/b5DtFUkWWqcFTzjTIUu61ru2P3mBw4qVUq7ZtDpelQDRrK9O8ZutmNHz6a4uPVymZ+DAXXbpyb/uBxa3Shlg9F8fnCbvxK/eG3MHacV3URuPMrSXBiLxgZ3Vms/EY96Jc5lP/Ooi2R6X/ExjqmAl3P51T+c8B5fWmcBcUr2Ok/5mzk53cU6cG/kiFHaFpriV1uxPMUgP17VGhi9sVAgMBAAGjggEIMIIBBDAOBgNVHQ8BAf8EBAMCAYYwHQYDVR0lBBYwFAYIKwYBBQUHAwIGCCsGAQUFBwMBMBIGA1UdEwEB/wQIMAYBAf8CAQAwHQYDVR0OBBYEFBQusxe3WFbLrlAJQOYfr52LFMLGMB8GA1UdIwQYMBaAFHm0WeZ7tuXkAXOACIjIGlj26ZtuMDIGCCsGAQUFBwEBBCYwJDAiBggrBgEFBQcwAoYWaHR0cDovL3gxLmkubGVuY3Iub3JnLzAnBgNVHR8EIDAeMBygGqAYhhZodHRwOi8veDEuYy5sZW5jci5vcmcvMCIGA1UdIAQbMBkwCAYGZ4EMAQIBMA0GCysGAQQBgt8TAQEBMA0GCSqGSIb3DQEBCwUAA4ICAQCFyk5HPqP3hUSFvNVneLKYY611TR6WPTNlclQtgaDqw+34IL9fzLdwALduO/ZelN7kIJ+m74uyA+eitRY8kc607TkC53wlikfmZW4/RvTZ8M6UK+5UzhK8jCdLuMGYL6KvzXGRSgi3yLgjewQtCPkIVz6D2QQzCkcheAmCJ8MqyJu5zlzyZMjAvnnAT45tRAxekrsu94sQ4egdRCnbWSDtY7kh+BImlJNXoB1lBMEKIq4QDUOXoRgffuDghje1WrG9ML+Hbisq/yFOGwXD9RiX8F6sw6W4avAuvDszue5L3sz85K+EC4Y/wFVDNvZo4TYXao6Z0f+lQKc0t8DQYzk1OXVu8rp2yJMC6alLbBfODALZvYH7n7do1AZls4I9d1P4jnkDrQoxB3UqQ9hVl3LEKQ73xF1OyK5GhDDX8oVfGKF5u+decIsH4YaTw7mP3GFxJSqv3+0lUFJoi5Lc5da149p90IdshCExroL1+7mryIkXPeFM5TgO9r0rvZaBFOvV2z0gp35Z0+L4WPlbuEjN/lxPFin+HlUjr8gRsI3qfJOQFy/9rKIJR0Y/8Omwt/8oTWgy1mdeHmmjk7j1nYsvC9JSQ6ZvMldlTTKB3zhThV1+XWYp6rjd5JW1zbVWEkLNxE7GJThEUG3szgBVGP7pSWTUTsqXnLRbwHOoq7hHwg==";
        chainCompleteCertificate = certificateService.createCertificate(chainCompleteCertificateContent, CertificateType.X509);
    }

    @AfterEach
    public void tearDown() {
        mockServer.stop();
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

    @Test
    public void testConstructingCertificateChainForSelfSignedCertificate() throws NotFoundException {
        CertificateChainResponseDto certificateChainResponseDto = certificateService.getCertificateChain(caCertificate.getSecuredUuid(), false);
        Assertions.assertEquals(0, certificateChainResponseDto.getCertificates().size());
        CertificateChainResponseDto certificateChainResponseDto2 = certificateService.getCertificateChain(caCertificate.getSecuredUuid(), true);
        Assertions.assertEquals(1, certificateChainResponseDto2.getCertificates().size());
    }

    @Test
    public void testGetCertificateChain() throws NotFoundException, IOException {
        mockServer.stubFor(WireMock.get("").willReturn(WireMock.ok("-----BEGIN CERTIFICATE-----\n")));
//        mockServer.stubFor(get(urlEqualTo("http://x1.i.lencr.org/"))
//                .willReturn(aResponse()
//                        .withStatus(200)
//                        .withBody("Your Custom Content")));
//        Mockito.when(OcspUtil.getChainFromAia(any())).thenReturn();
        CertificateChainResponseDto certificateChainResponseDto = certificateService.getCertificateChain(chainIncompleteCertificate.getSecuredUuid(), false);
        Assertions.assertFalse(certificateChainResponseDto.isCompleteChain());
        CertificateChainResponseDto certificateChainCompleteResponseDto = certificateService.getCertificateChain(chainCompleteCertificate.getSecuredUuid(), false);
        Assertions.assertTrue(certificateChainCompleteResponseDto.isCompleteChain());
        List<CertificateDetailDto> certificates = certificateChainCompleteResponseDto.getCertificates();
        Assertions.assertEquals(chainCompleteCertificate.getIssuerDn(), certificates.get(certificates.size()-1).getSubjectDn());
        Assertions.assertEquals(1, certificates.size());
        CertificateChainResponseDto certificateChainCompleteResponseDto2 = certificateService.getCertificateChain(chainCompleteCertificate.getSecuredUuid(), true);
        Assertions.assertEquals(2, certificateChainCompleteResponseDto2.getCertificates().size());
        Assertions.assertTrue(certificateChainCompleteResponseDto.isCompleteChain());
    }

    @Test
    public void testDownloadCertificateChain() throws NotFoundException, CertificateException {
        CertificateChainDownloadResponseDto certificateChainDownloadResponseDto = certificateService.downloadCertificateChain(chainIncompleteCertificate.getSecuredUuid(), CertificateFormat.PEM, true);
        Assertions.assertFalse(certificateChainDownloadResponseDto.isCompleteChain());
        CertificateChainDownloadResponseDto certificateChainResponseDto2 = certificateService.downloadCertificateChain(chainCompleteCertificate.getSecuredUuid(), CertificateFormat.PEM, false);
        Assertions.assertTrue(certificateChainResponseDto2.isCompleteChain());
        CertificateChainDownloadResponseDto certificateChainResponseDto3 = certificateService.downloadCertificateChain(chainCompleteCertificate.getSecuredUuid(), CertificateFormat.PKCS7, false);
        Assertions.assertTrue(certificateChainResponseDto3.isCompleteChain());
    }




}
