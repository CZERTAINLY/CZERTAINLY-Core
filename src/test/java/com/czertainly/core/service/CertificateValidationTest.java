package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.authority.CertificateRevocationReason;
import com.czertainly.api.model.core.certificate.*;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.raprofile.RaProfileCertificateValidationSettingsUpdateDto;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.MetaDefinitions;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.util.Store;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

@SpringBootTest
@Transactional
@Rollback
public class CertificateValidationTest extends BaseSpringBootTest {

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private CrlService crlService;

    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;

    @Autowired
    private CrlRepository crlRepository;

    @Autowired
    private CrlEntryRepository crlEntryRepository;

    @Autowired
    private RaProfileRepository raProfileRepository;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;

    private Certificate certificate;

    private Certificate caCertificate;

    private Certificate chainIncompleteCertificate;

    private Certificate chainCompleteCertificate;

    private WireMockServer mockServer;

    @BeforeEach
    public void setUp() throws GeneralSecurityException, IOException, com.czertainly.api.exception.CertificateException {
        mockServer = new WireMockServer(0);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());
        InputStream keyStoreStream = CertificateServiceTest.class.getClassLoader().getResourceAsStream("client1.p12");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(keyStoreStream, "123456".toCharArray());

        X509Certificate x509Cert = (X509Certificate) keyStore.getCertificate("1");

        CertificateContent certificateContent = new CertificateContent();
        certificateContent.setContent(Base64.getEncoder().encodeToString(x509Cert.getEncoded()));
        certificateContent = certificateContentRepository.save(certificateContent);

        certificate = new Certificate();
        certificate.setSubjectDn("testCertificate");
        certificate.setIssuerDn("testCertificate");
        certificate.setSerialNumber("123456789");
        certificate.setState(CertificateState.ISSUED);
        certificate.setValidationStatus(CertificateValidationStatus.VALID);
        certificate.setCertificateType(CertificateType.X509);
        certificate.setNotBefore(new Date());
        certificate.setNotAfter(new Date());
        certificate.setCertificateContent(certificateContent);
        certificate = certificateRepository.save(certificate);

        String caCertificateContent = "MIIFyzCCA7OgAwIBAgIUOymQEJz0e6+2cDE/" + "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";

        caCertificate = certificateService.createCertificate(caCertificateContent, CertificateType.X509);

        certificateRepository.save(caCertificate);

        String chainCertificateContent = "MIICjTCCAjOgAwIBAgIQf/NXf/Y8fKN+BkL4yLhikDAKBggqhkjOPQQDAjBQMSQwIgYDVQQLExtHbG9iYWxTaWduIEVDQyBSb290IENBIC0gUjQxEzARBgNVBAoTCkdsb2JhbFNpZ24xEzARBgNVBAMTCkdsb2JhbFNpZ24wHhcNMjMxMjEzMDkwMDAwWhcNMjkwMjIwMTQwMDAwWjA7MQswCQYDVQQGEwJVUzEeMBwGA1UEChMVR29vZ2xlIFRydXN0IFNlcnZpY2VzMQwwCgYDVQQDEwNXRTIwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQ1fh/yFO2QfeGeKjRDhsHVlugncN+eBMupyoZ5CwhNRorCdKS72b/u/SPXOPNL71QX4b7nylUlqAwwrC1dTqFRo4IBAjCB/zAOBgNVHQ8BAf8EBAMCAYYwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMBIGA1UdEwEB/wQIMAYBAf8CAQAwHQYDVR0OBBYEFHW+xHeuifZEN33PsWgfHRrr3DRZMB8GA1UdIwQYMBaAFFSwe61FuOJAf/sKbvu+M8k8o4TVMDYGCCsGAQUFBwEBBCowKDAmBggrBgEFBQcwAoYaaHR0cDovL2kucGtpLmdvb2cvZ3NyNC5jcnQwLQYDVR0fBCYwJDAioCCgHoYcaHR0cDovL2MucGtpLmdvb2cvci9nc3I0LmNybDATBgNVHSAEDDAKMAgGBmeBDAECATAKBggqhkjOPQQDAgNIADBFAiBbsQQLhG0VbSTee24xTExpfZjtvxSr0QUeF5ynbsLfsAIhALzHr/nsu7YViuT13ZNVPKTQ4UWHvxnqxJZRGRgNHE4w";
        chainIncompleteCertificate = certificateService.createCertificate(chainCertificateContent, CertificateType.X509);

        String chainCompleteCertificateContent = "MIIFFjCCAv6gAwIBAgIRAJErCErPDBinU/bWLiWnX1owDQYJKoZIhvcNAQELBQAwTzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2VhcmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMjAwOTA0MDAwMDAwWhcNMjUwOTE1MTYwMDAwWjAyMQswCQYDVQQGEwJVUzEWMBQGA1UEChMNTGV0J3MgRW5jcnlwdDELMAkGA1UEAxMCUjMwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC7AhUozPaglNMPEuyNVZLD+ILxmaZ6QoinXSaqtSu5xUyxr45r+XXIo9cPR5QUVTVXjJ6oojkZ9YI8QqlObvU7wy7bjcCwXPNZOOftz2nwWgsbvsCUJCWH+jdxsxPnHKzhm+/b5DtFUkWWqcFTzjTIUu61ru2P3mBw4qVUq7ZtDpelQDRrK9O8ZutmNHz6a4uPVymZ+DAXXbpyb/uBxa3Shlg9F8fnCbvxK/eG3MHacV3URuPMrSXBiLxgZ3Vms/EY96Jc5lP/Ooi2R6X/ExjqmAl3P51T+c8B5fWmcBcUr2Ok/5mzk53cU6cG/kiFHaFpriV1uxPMUgP17VGhi9sVAgMBAAGjggEIMIIBBDAOBgNVHQ8BAf8EBAMCAYYwHQYDVR0lBBYwFAYIKwYBBQUHAwIGCCsGAQUFBwMBMBIGA1UdEwEB/wQIMAYBAf8CAQAwHQYDVR0OBBYEFBQusxe3WFbLrlAJQOYfr52LFMLGMB8GA1UdIwQYMBaAFHm0WeZ7tuXkAXOACIjIGlj26ZtuMDIGCCsGAQUFBwEBBCYwJDAiBggrBgEFBQcwAoYWaHR0cDovL3gxLmkubGVuY3Iub3JnLzAnBgNVHR8EIDAeMBygGqAYhhZodHRwOi8veDEuYy5sZW5jci5vcmcvMCIGA1UdIAQbMBkwCAYGZ4EMAQIBMA0GCysGAQQBgt8TAQEBMA0GCSqGSIb3DQEBCwUAA4ICAQCFyk5HPqP3hUSFvNVneLKYY611TR6WPTNlclQtgaDqw+34IL9fzLdwALduO/ZelN7kIJ+m74uyA+eitRY8kc607TkC53wlikfmZW4/RvTZ8M6UK+5UzhK8jCdLuMGYL6KvzXGRSgi3yLgjewQtCPkIVz6D2QQzCkcheAmCJ8MqyJu5zlzyZMjAvnnAT45tRAxekrsu94sQ4egdRCnbWSDtY7kh+BImlJNXoB1lBMEKIq4QDUOXoRgffuDghje1WrG9ML+Hbisq/yFOGwXD9RiX8F6sw6W4avAuvDszue5L3sz85K+EC4Y/wFVDNvZo4TYXao6Z0f+lQKc0t8DQYzk1OXVu8rp2yJMC6alLbBfODALZvYH7n7do1AZls4I9d1P4jnkDrQoxB3UqQ9hVl3LEKQ73xF1OyK5GhDDX8oVfGKF5u+decIsH4YaTw7mP3GFxJSqv3+0lUFJoi5Lc5da149p90IdshCExroL1+7mryIkXPeFM5TgO9r0rvZaBFOvV2z0gp35Z0+L4WPlbuEjN/lxPFin+HlUjr8gRsI3qfJOQFy/9rKIJR0Y/8Omwt/8oTWgy1mdeHmmjk7j1nYsvC9JSQ6ZvMldlTTKB3zhThV1+XWYp6rjd5JW1zbVWEkLNxE7GJThEUG3szgBVGP7pSWTUTsqXnLRbwHOoq7hHwg==";
        chainCompleteCertificate = certificateService.createCertificate(chainCompleteCertificateContent, CertificateType.X509);
    }


    @Test
    void testValidateCertificate() {
        certificateService.validate(certificate);

        String result = certificate.getCertificateValidationResult();
        Assertions.assertNotNull(result);
        Assertions.assertTrue(StringUtils.isNotBlank(result));

        Map<CertificateValidationCheck, CertificateValidationCheckDto> resultMap = MetaDefinitions.deserializeValidation(result);
        Assertions.assertNotNull(resultMap);
        Assertions.assertFalse(resultMap.isEmpty());

        CertificateValidationCheckDto signatureVerification = resultMap.get(CertificateValidationCheck.CERTIFICATE_CHAIN);
        Assertions.assertNotNull(signatureVerification);
        Assertions.assertEquals(CertificateValidationStatus.INVALID, signatureVerification.getStatus());
    }

    @Test
    void testExpiringCertificate() throws CertificateException, NoSuchAlgorithmException, OperatorCreationException {

        LocalDate today = LocalDate.now();
        // Expiring according to platform settings with null RA Profile
        LocalDate expiringDate = today.plusDays(29);
        X509Certificate x509Certificate = createCertificateWithCustomNotAfter(Date.from(expiringDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        certificate.getCertificateContent().setContent(Base64.getEncoder().encodeToString(x509Certificate.getEncoded()));

        certificateService.validate(certificate);
        Map<CertificateValidationCheck, CertificateValidationCheckDto> resultMap = MetaDefinitions.deserializeValidation(certificate.getCertificateValidationResult());
        Assertions.assertEquals(CertificateValidationStatus.EXPIRING, resultMap.get(CertificateValidationCheck.CERTIFICATE_VALIDITY).getStatus());

        // Expiring according to platform settings with not null RA Profile, but null validation enabled
        certificate.setRaProfile(getRaProfile(new RaProfileCertificateValidationSettingsUpdateDto()));
        certificateService.validate(certificate);
        resultMap = MetaDefinitions.deserializeValidation(certificate.getCertificateValidationResult());
        Assertions.assertEquals(CertificateValidationStatus.EXPIRING, resultMap.get(CertificateValidationCheck.CERTIFICATE_VALIDITY).getStatus());

        // Expiring according to RA Profile settings
        RaProfileCertificateValidationSettingsUpdateDto validationSettingsUpdateDto = new RaProfileCertificateValidationSettingsUpdateDto();
        validationSettingsUpdateDto.setEnabled(true);
        validationSettingsUpdateDto.setExpiringThreshold(10);
        certificate.setRaProfile(getRaProfile(validationSettingsUpdateDto));

        // Certificate is not yet expiring
        certificateService.validate(certificate);
        resultMap = MetaDefinitions.deserializeValidation(certificate.getCertificateValidationResult());
        Assertions.assertEquals(CertificateValidationStatus.VALID, resultMap.get(CertificateValidationCheck.CERTIFICATE_VALIDITY).getStatus());

        // Certificate is expiring
        expiringDate = today.plusDays(9);
        x509Certificate = createCertificateWithCustomNotAfter(Date.from(expiringDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        certificate.getCertificateContent().setContent(Base64.getEncoder().encodeToString(x509Certificate.getEncoded()));
        certificateService.validate(certificate);
        resultMap = MetaDefinitions.deserializeValidation(certificate.getCertificateValidationResult());
        Assertions.assertEquals(CertificateValidationStatus.EXPIRING, resultMap.get(CertificateValidationCheck.CERTIFICATE_VALIDITY).getStatus());
    }

    private RaProfile getRaProfile(RaProfileCertificateValidationSettingsUpdateDto validationUpdateDto) {
        RaProfile raProfile = new RaProfile();
        raProfile.setEnabled(true);
        AuthorityInstanceReference authorityInstanceReference = new AuthorityInstanceReference();
        authorityInstanceReference.setStatus("connected");
        Connector connector = new Connector();
        connector.setStatus(ConnectorStatus.CONNECTED);
        connectorRepository.save(connector);
        authorityInstanceReference.setConnector(connector);
        authorityInstanceReferenceRepository.save(authorityInstanceReference);
        raProfile.setAuthorityInstanceReference(authorityInstanceReference);
        raProfile.setValidationEnabled(validationUpdateDto.getEnabled());
        raProfile.setExpiringThreshold(validationUpdateDto.getExpiringThreshold());
        raProfileRepository.save(raProfile);
        return raProfile;
    }

    private X509Certificate createCertificateWithCustomNotAfter(Date notAfter) throws NoSuchAlgorithmException, OperatorCreationException, CertificateException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048, new SecureRandom());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        X500Principal issuer = new X500Principal("CN=" + certificate.getIssuerDn());
        Date notBefore = new Date();
        BigInteger serialNumber = BigInteger.ONE;

        X509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                issuer, serialNumber, notBefore, notAfter, issuer, keyPair.getPublic()
        );

        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate());
        X509CertificateHolder certificateHolder = certificateBuilder.build(contentSigner);

        return new JcaX509CertificateConverter().getCertificate(certificateHolder);
    }

    @Test
    void testConstructingCertificateChainForSelfSignedCertificate() throws NotFoundException {
        CertificateChainResponseDto certificateChainResponseDto = certificateService.getCertificateChain(caCertificate.getSecuredUuid(), false);
        Assertions.assertEquals(0, certificateChainResponseDto.getCertificates().size());
        CertificateChainResponseDto certificateChainResponseDto2 = certificateService.getCertificateChain(caCertificate.getSecuredUuid(), true);
        Assertions.assertEquals(1, certificateChainResponseDto2.getCertificates().size());
    }

    @Test
    void testGetCertificateChain() throws NotFoundException {
        CertificateChainResponseDto certificateChainResponseDto = certificateService.getCertificateChain(chainIncompleteCertificate.getSecuredUuid(), false);
        Assertions.assertTrue(certificateChainResponseDto.isCompleteChain());
        CertificateChainResponseDto certificateChainCompleteResponseDto = certificateService.getCertificateChain(chainCompleteCertificate.getSecuredUuid(), false);
        Assertions.assertTrue(certificateChainCompleteResponseDto.isCompleteChain());
        List<CertificateDetailDto> certificates = certificateChainCompleteResponseDto.getCertificates();
        Assertions.assertEquals(chainCompleteCertificate.getIssuerDn(), certificates.get(certificates.size() - 1).getSubjectDn());
        Assertions.assertEquals(1, certificates.size());
        CertificateChainResponseDto certificateChainCompleteResponseDto2 = certificateService.getCertificateChain(chainCompleteCertificate.getSecuredUuid(), true);
        Assertions.assertEquals(2, certificateChainCompleteResponseDto2.getCertificates().size());
        Assertions.assertTrue(certificateChainCompleteResponseDto.isCompleteChain());
    }

    @Test
    void testDownloadCertificateChain() throws NotFoundException, CertificateException, CMSException, IOException {
        CertificateChainDownloadResponseDto certificateChainIncompletePEMDownloadResponseDto = certificateService.downloadCertificateChain(chainIncompleteCertificate.getSecuredUuid(), CertificateFormat.RAW, true, CertificateFormatEncoding.PEM);
        Assertions.assertEquals(2, getNumberOfCertificatesInPem(certificateChainIncompletePEMDownloadResponseDto.getContent()));
        Assertions.assertTrue(certificateChainIncompletePEMDownloadResponseDto.isCompleteChain());
        CertificateChainDownloadResponseDto certificateChainCompletePEMResponseDto = certificateService.downloadCertificateChain(chainCompleteCertificate.getSecuredUuid(), CertificateFormat.RAW, true, CertificateFormatEncoding.PEM);
        Assertions.assertTrue(certificateChainCompletePEMResponseDto.isCompleteChain());
        Assertions.assertEquals(2, getNumberOfCertificatesInPem(certificateChainCompletePEMResponseDto.getContent()));
        CertificateChainDownloadResponseDto certificateChainCompletePKCS7ResponseDto = certificateService.downloadCertificateChain(chainCompleteCertificate.getSecuredUuid(), CertificateFormat.PKCS7, true, CertificateFormatEncoding.PEM);
        Assertions.assertTrue(certificateChainCompletePKCS7ResponseDto.isCompleteChain());
        Assertions.assertEquals(2, getNumberOfCertificatesInPkcs7(certificateChainCompletePKCS7ResponseDto.getContent()));
        CertificateChainDownloadResponseDto certificateChainResponseDto = certificateService.downloadCertificateChain(chainCompleteCertificate.getSecuredUuid(), CertificateFormat.PKCS7, false, CertificateFormatEncoding.PEM);
        Assertions.assertTrue(certificateChainResponseDto.isCompleteChain());
        Assertions.assertEquals(1, getNumberOfCertificatesInPkcs7(certificateChainResponseDto.getContent()));

    }

    @Test
    void testCrlProcessing() throws GeneralSecurityException, OperatorCreationException, IOException, NotFoundException {

        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048);
        KeyPair pair = keyPairGen.generateKeyPair();

        // prepare certs
        X509Certificate x509CaCertificate = CertificateUtil.getX509Certificate(caCertificate.getCertificateContent().getContent());

        List<String> crlUrls = List.of(mockServer.baseUrl() + "/crl1.crl", mockServer.baseUrl() + "/crl2.crl");
        X509Certificate certificateWithCrl = createSelfSignedCertificateWithCrl("testCrl", crlUrls, null, BigInteger.ONE);
        Certificate certificateWithCrlEntity = certificateService.createCertificateEntity(certificateWithCrl);
        certificateWithCrlEntity.setTrustedCa(true);
        certificateRepository.save(certificateWithCrlEntity);

        X509Certificate certificateWithDelta = createSelfSignedCertificateWithCrl("testCrlWithDelta", crlUrls, List.of(mockServer.baseUrl() + "/deltaCrl"), BigInteger.TWO);
        Certificate certificateWithCrlDeltaEntity = certificateService.createCertificateEntity(certificateWithDelta);
        certificateWithCrlDeltaEntity.setTrustedCa(true);
        certificateRepository.save(certificateWithCrlDeltaEntity);

        // Test no CRL available
        var validationResult = certificateService.getCertificateValidationResult(certificateWithCrlEntity.getSecuredUuid());
        Assertions.assertEquals(CertificateValidationStatus.NOT_CHECKED, validationResult.getValidationChecks().get(CertificateValidationCheck.CRL_VERIFICATION).getStatus());

        // Test CRL without the end certificate and without delta
        X509CRL emptyX509Crl = createEmptyCRL(x509CaCertificate, pair.getPrivate());
        byte[] emptyX509CrlBytes = emptyX509Crl.getEncoded();
        stubCrlPoint("/crl1.crl", emptyX509CrlBytes);
        validationResult = certificateService.getCertificateValidationResult(certificateWithCrlEntity.getSecuredUuid());
        Assertions.assertEquals(CertificateValidationStatus.VALID, validationResult.getValidationChecks().get(CertificateValidationCheck.CRL_VERIFICATION).getStatus());
        UUID crlUuid = crlService.getCurrentCrl(certificateWithCrl, certificateWithCrl);
        Assertions.assertNull(crlService.findCrlEntryForCertificate(certificateWithCrl.getSerialNumber().toString(16), crlUuid));

        crlRepository.deleteAll();
        crlRepository.findAll();
        // Test updating existing CRL
        Crl oldCrl = new Crl();
        oldCrl.setNextUpdate(new Date());
        oldCrl.setCrlNumber(String.valueOf(1));
        oldCrl.setSerialNumber("1");
        oldCrl.setCrlIssuerDn("2.5.4.3=testCrl");
        oldCrl.setIssuerDn("2.5.4.3=testCrl");

        crlRepository.save(oldCrl);
        CrlEntry crlEntry = new CrlEntry();
        CrlEntryId crlEntryId = new CrlEntryId();
        crlEntryId.setSerialNumber("2");
        crlEntryId.setCrlUuid(oldCrl.getUuid());
        crlEntry.setId(crlEntryId);
        crlEntry.setCrl(oldCrl);
        crlEntry.setRevocationDate(new Date());
        crlEntry.setRevocationReason(CertificateRevocationReason.CERTIFICATE_HOLD);
        oldCrl.setCrlEntries(List.of(crlEntry));
        certificateService.getCertificateValidationResult(certificateWithCrlEntity.getSecuredUuid());
        Assertions.assertTrue(crlEntryRepository.findById(crlEntryId).isEmpty());
        crlRepository.deleteAll();

        // Test CRL with revoked certificate and without delta and with one invalid CRL distribution point
        mockServer.removeStubMapping(mockServer.getStubMappings().get(0));
        Map<BigInteger, Integer> crlEntries = new HashMap<>();
        crlEntries.put(certificateWithCrl.getSerialNumber(), CRLReason.keyCompromise);
        X509CRL x509CrlRevokedCert = addRevocationToCRL(pair.getPrivate(), "SHA256WithRSAEncryption", emptyX509Crl, crlEntries);
        stubCrlPoint("/crl2.crl", x509CrlRevokedCert.getEncoded());
        validationResult = certificateService.getCertificateValidationResult(certificateWithCrlEntity.getSecuredUuid());
        Assertions.assertEquals(CertificateValidationStatus.REVOKED, validationResult.getValidationChecks().get(CertificateValidationCheck.CRL_VERIFICATION).getStatus());
        UUID crlWithRevokedUuid = crlService.getCurrentCrl(certificateWithCrl, certificateWithCrl);
        Assertions.assertNotNull(crlService.findCrlEntryForCertificate(certificateWithCrl.getSerialNumber().toString(16), crlWithRevokedUuid));

        // Test properly set deltaCrl
        Crl crlWithRevoked = crlRepository.findByUuid(SecuredUUID.fromUUID(crlWithRevokedUuid)).get();
        X509CRL deltaCrl = createEmptyDeltaCRL(x509CaCertificate.getSubjectX500Principal(), pair.getPrivate(), BigInteger.valueOf(Integer.parseInt(crlWithRevoked.getCrlNumber())), BigInteger.ONE);
        stubCrlPoint("/deltaCrl", deltaCrl.getEncoded());
        UUID crlWithDeltaUuid = crlService.getCurrentCrl(certificateWithDelta, certificateWithDelta);
        Crl crlWithDelta = crlRepository.findByUuid(SecuredUUID.fromUUID(crlWithDeltaUuid)).get();
        Assertions.assertNotNull(crlWithDelta.getCrlNumberDelta());
        Assertions.assertNotNull(crlWithDelta.getNextUpdateDelta());

        // Test improperly set deltaCrl because of CRL numbers
        crlRepository.delete(crlWithDelta);
        X509CRL deltaCrl2 = createEmptyDeltaCRL(x509CaCertificate.getSubjectX500Principal(), pair.getPrivate(), BigInteger.valueOf(3), BigInteger.TWO);
        stubCrlPoint("/deltaCrl", deltaCrl2.getEncoded());
        validationResult = certificateService.getCertificateValidationResult(certificateWithCrlDeltaEntity.getSecuredUuid());
        Assertions.assertEquals(CertificateValidationStatus.FAILED, validationResult.getValidationChecks().get(CertificateValidationCheck.CRL_VERIFICATION).getStatus());
        Assertions.assertThrows(ValidationException.class, () -> crlService.getCurrentCrl(certificateWithDelta, certificateWithDelta));

        // Test improperly set deltaCrl
        crlRepository.deleteAll();
        X509CRL deltaCrlIssuerNotMatching = createEmptyDeltaCRL(certificateWithDelta.getIssuerX500Principal(), pair.getPrivate(), BigInteger.valueOf(Integer.parseInt(crlWithRevoked.getCrlNumber())), BigInteger.TWO);
        stubCrlPoint("/deltaCrl", deltaCrlIssuerNotMatching.getEncoded());
        validationResult = certificateService.getCertificateValidationResult(certificateWithCrlDeltaEntity.getSecuredUuid());
        Assertions.assertEquals(CertificateValidationStatus.FAILED, validationResult.getValidationChecks().get(CertificateValidationCheck.CRL_VERIFICATION).getStatus());
        Assertions.assertThrows(ValidationException.class, () -> crlService.getCurrentCrl(certificateWithDelta, certificateWithDelta));



        // Test deltaCrl with revoked cert and with one certificate to remove from CRL entries
        crlRepository.deleteAll();
        X509CRL deltaCrl3 = createEmptyDeltaCRL(x509CaCertificate.getSubjectX500Principal(), pair.getPrivate(), BigInteger.valueOf(Integer.parseInt(crlWithRevoked.getCrlNumber())), BigInteger.TWO);
        crlEntries.put(BigInteger.valueOf(123), CRLReason.removeFromCRL);
        crlEntries.put(BigInteger.valueOf(1234), CRLReason.keyCompromise);
        crlEntries.put(BigInteger.TWO, CRLReason.cACompromise);
        X509CRL deltaCrlWithRevoked = addRevocationToCRL(pair.getPrivate(), "SHA256WithRSAEncryption", deltaCrl3, crlEntries);

        crlEntries.remove(x509CaCertificate.getSerialNumber());
        crlEntries.remove(BigInteger.TWO);
        crlEntries.put(BigInteger.valueOf(123), CRLReason.keyCompromise);
        crlEntries.put(BigInteger.valueOf(1234), CRLReason.cACompromise);
        X509CRL crlWithCertificateToRemove = addRevocationToCRL(pair.getPrivate(), "SHA256WithRSAEncryption", emptyX509Crl, crlEntries);

        stubCrlPoint("/crl1.crl", crlWithCertificateToRemove.getEncoded());
        stubCrlPoint("/deltaCrl", deltaCrlWithRevoked.getEncoded());
        validationResult = certificateService.getCertificateValidationResult(certificateWithCrlDeltaEntity.getSecuredUuid());
        Assertions.assertEquals(CertificateValidationStatus.REVOKED, validationResult.getValidationChecks().get(CertificateValidationCheck.CRL_VERIFICATION).getStatus());
        UUID crlWithDelta2Uuid = crlService.getCurrentCrl(certificateWithDelta, certificateWithDelta);
        Assertions.assertNotNull(crlService.findCrlEntryForCertificate(certificateWithDelta.getSerialNumber().toString(16), crlWithDelta2Uuid));
        Assertions.assertNull(crlService.findCrlEntryForCertificate(BigInteger.valueOf(123).toString(16), crlWithRevokedUuid));
        CrlEntryId crlEntryId1 = new CrlEntryId();
        crlEntryId1.setCrlUuid(crlWithDelta2Uuid);
        crlEntryId1.setSerialNumber(BigInteger.valueOf(1234).toString(16));
        Assertions.assertEquals(CertificateRevocationReason.KEY_COMPROMISE, crlEntryRepository.findById(crlEntryId1).get().getRevocationReason());

    }


    private void stubCrlPoint(String urlPart, byte[] body) {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching(urlPart))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Set-Cookie", "session_id=91837492837")
                        .withHeader("Set-Cookie", "split_test_group=B")
                        .withHeader("Cache-Control", "no-cache")
                        .withBody(body)));
    }

    private X509Certificate createSelfSignedCertificateWithCrl(String commonName, List<String> crlUrls, List<String> deltaCrlUrls, BigInteger serialNumber) throws CertIOException, NoSuchAlgorithmException, OperatorCreationException, CertificateException {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048);
        KeyPair pair = keyPairGen.generateKeyPair();
        SubjectPublicKeyInfo subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(pair.getPublic().getEncoded());

        X500Name x500Name = new X500Name("CN=" + commonName);
        X509v3CertificateBuilder certificateBuilder = new X509v3CertificateBuilder(x500Name, serialNumber, new Date(0), new Date(Long.MAX_VALUE), x500Name, subjectPublicKeyInfo);
        CRLDistPoint crlDistPoint = new CRLDistPoint(createCrlDistributionPoints(crlUrls));
        certificateBuilder.addExtension(Extension.cRLDistributionPoints, false, crlDistPoint);
        if (deltaCrlUrls != null) {
            CRLDistPoint deltaCrlDistPoint = new CRLDistPoint(createCrlDistributionPoints(deltaCrlUrls));
            certificateBuilder.addExtension(Extension.freshestCRL, false, deltaCrlDistPoint);
        }
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                .build(pair.getPrivate());
        X509CertificateHolder holder = certificateBuilder.build(signer);
        JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
        converter.setProvider(new BouncyCastleProvider());
        return converter.getCertificate(holder);
    }


    private int getNumberOfCertificatesInPkcs7(String content) throws CMSException {
        String pkcs7Data = new String(Base64.getDecoder().decode(content));
        pkcs7Data = pkcs7Data.replace("-----BEGIN PKCS7-----", "").replace("-----END PKCS7-----", "").replaceAll("\\s", "");
        CMSSignedData signedData = new CMSSignedData(Base64.getDecoder().decode(pkcs7Data));
        Store<X509CertificateHolder> certificates = signedData.getCertificates();
        Collection<X509CertificateHolder> certCollection = certificates.getMatches(null);
        return certCollection.size();
    }

    private int getNumberOfCertificatesInPem(String content) throws IOException {
        byte[] pemCertificateChainBytes = Base64.getDecoder().decode(content);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(pemCertificateChainBytes);
        PemReader reader = new PemReader(new InputStreamReader(inputStream));
        List<PemObject> pemObjects = new ArrayList<>();
        PemObject pemObject;
        while ((pemObject = reader.readPemObject()) != null) {
            pemObjects.add(pemObject);
        }
        reader.close();
        return pemObjects.size();
    }

    public static Date calculateDate(int hoursInFuture) {
        long secs = System.currentTimeMillis() / 1000;
        return new Date((secs + ((long) hoursInFuture * 60 * 60)) * 1000);
    }

    private X509CRL createEmptyCRL(X509Certificate caCert, PrivateKey caKey) throws CRLException, OperatorCreationException, CertIOException {
        X509v2CRLBuilder crlGen = new X509v2CRLBuilder(X500Name.getInstance(caCert.getSubjectX500Principal().getEncoded()), calculateDate(0));
        crlGen.setNextUpdate(calculateDate(24 * 7));
        crlGen.addExtension(Extension.cRLNumber,
                false,
                new CRLNumber(BigInteger.TWO));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                .setProvider("BC").build(caKey);

        JcaX509CRLConverter converter = new JcaX509CRLConverter().setProvider("BC");
        return converter.getCRL(crlGen.build(signer));
    }

    private X509CRL createEmptyDeltaCRL(X500Principal issuerDnPrincipal, PrivateKey caKey, BigInteger deltaCrlIndicator, BigInteger deltaCrlNumber) throws CRLException, OperatorCreationException, CertIOException {
        X509v2CRLBuilder crlGen = new X509v2CRLBuilder(X500Name.getInstance(issuerDnPrincipal.getEncoded()), calculateDate(0));
        crlGen.setNextUpdate(calculateDate(24 * 7));
        crlGen.addExtension(Extension.cRLNumber, false, new CRLNumber(deltaCrlNumber));
        crlGen.addExtension(Extension.deltaCRLIndicator, false, new CRLNumber(deltaCrlIndicator));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                .setProvider("BC").build(caKey);

        JcaX509CRLConverter converter = new JcaX509CRLConverter().setProvider("BC");
        return converter.getCRL(crlGen.build(signer));
    }

    public X509CRL addRevocationToCRL(PrivateKey caKey, String sigAlg, X509CRL crl, Map<BigInteger, Integer> serialNumberAndRevocationReasonMap) throws IOException, GeneralSecurityException, OperatorCreationException {
        JcaX509v2CRLBuilder crlGen = new JcaX509v2CRLBuilder(crl);
        crlGen.setNextUpdate(calculateDate(24 * 7));

        // add revocations
        for (BigInteger serialNumber : serialNumberAndRevocationReasonMap.keySet()) {
            ExtensionsGenerator extGen = new ExtensionsGenerator();
            extGen.addExtension(Extension.reasonCode, false, org.bouncycastle.asn1.x509.CRLReason.lookup(serialNumberAndRevocationReasonMap.get(serialNumber)));
            crlGen.addCRLEntry(serialNumber,
                    new Date(), extGen.generate());
        }

        ContentSigner signer = new JcaContentSignerBuilder(sigAlg)
                .setProvider("BC").build(caKey);
        JcaX509CRLConverter converter = new JcaX509CRLConverter().setProvider("BC");

        return converter.getCRL(crlGen.build(signer));
    }

    private DistributionPoint[] createCrlDistributionPoints(List<String> urls) {
        List<DistributionPoint> list = new ArrayList<>();
        for (String url : urls) {
            DERIA5String deria5String = new DERIA5String(url);
            GeneralName generalName = new GeneralName(GeneralName.uniformResourceIdentifier, deria5String);
            DistributionPointName distributionPointName = new DistributionPointName(new GeneralNames(generalName));
            list.add(new DistributionPoint(distributionPointName, null, null));
        }
        return list.toArray(new DistributionPoint[0]);
    }


}
