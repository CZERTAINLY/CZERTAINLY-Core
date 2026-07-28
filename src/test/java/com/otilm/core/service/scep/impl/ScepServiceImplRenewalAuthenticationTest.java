package com.otilm.core.service.scep.impl;

import com.otilm.api.exception.NotFoundException;
import com.otilm.api.exception.ScepException;
import com.otilm.api.model.core.certificate.CertificateState;
import com.otilm.api.model.core.certificate.CertificateValidationStatus;
import com.otilm.api.model.core.scep.FailInfo;
import com.otilm.core.dao.entity.Certificate;
import com.otilm.core.dao.entity.RaProfile;
import com.otilm.core.dao.entity.scep.ScepProfile;
import com.otilm.core.service.CertificateInternalService;
import com.otilm.core.service.scep.message.ScepRequest;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ScepServiceImpl#authenticateRenewal}, the positive renewal classification that
 * decides whether a request may enroll without the profile's challenge password. The waiver requires the
 * request to prove possession of an existing certificate's key (RFC 8894 §3.3.1.2), so an unresolvable
 * or foreign signer certificate must never earn it.
 */
class ScepServiceImplRenewalAuthenticationTest {

    private static final String SUBJECT_DN = "CN=renewal-client";
    private static final UUID RA_PROFILE_UUID = UUID.randomUUID();

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private ScepServiceImpl service;
    private ScepProfile profile;
    private CertificateInternalService certificateService;
    private KeyPair clientKeyPair;
    private X509Certificate clientCertificate;

    @BeforeEach
    void setUp() throws Exception {
        service = new ScepServiceImpl();
        profile = mock(ScepProfile.class);
        certificateService = mock(CertificateInternalService.class);

        RaProfile raProfile = new RaProfile();
        raProfile.setUuid(RA_PROFILE_UUID);

        ReflectionTestUtils.setField(service, "scepProfile", profile);
        ReflectionTestUtils.setField(service, "raProfile", raProfile);
        ReflectionTestUtils.setField(service, "certificateService", certificateService);

        when(profile.getRenewalThreshold()).thenReturn(30);

        clientKeyPair = generateRsaKeyPair();
        clientCertificate = selfSignedCertificate(clientKeyPair, SUBJECT_DN);
    }

    @Test
    void noSignerCertificate_isNotARenewal() throws Exception {
        ScepRequest request = mock(ScepRequest.class);
        when(request.getSignerCertificate()).thenReturn(null);

        assertFalse(service.authenticateRenewal(request));
    }

    @Test
    void signerCertificateNotInInventory_isNotARenewal() throws Exception {
        when(certificateService.getCertificateEntityByFingerprint(any()))
                .thenThrow(new NotFoundException(Certificate.class, "fingerprint"));

        assertFalse(service.authenticateRenewal(renewalRequest(SUBJECT_DN, true)));
    }

    @Test
    void resolvedCertificateWithVerifiedSignature_isAnAuthenticatedRenewal() throws Exception {
        when(certificateService.getCertificateEntityByFingerprint(any()))
                .thenReturn(inventoryCertificate(RA_PROFILE_UUID));

        assertTrue(service.authenticateRenewal(renewalRequest(SUBJECT_DN, true)));
    }

    @Test
    void failedSignatureVerification_rejectedWithBadMessageCheck() throws Exception {
        when(certificateService.getCertificateEntityByFingerprint(any()))
                .thenReturn(inventoryCertificate(RA_PROFILE_UUID));

        ScepException thrown = assertThrows(ScepException.class,
                () -> service.authenticateRenewal(renewalRequest(SUBJECT_DN, false)));
        assertEquals(FailInfo.BAD_MESSAGE_CHECK, thrown.getFailInfo());
    }

    /**
     * The signer certificate belongs to a different RA profile, so it is not entitled to this SCEP
     * profile's enrollment without the shared secret: renewal policy still applies, but the challenge
     * password waiver is withheld.
     */
    @Test
    void certificateOfAnotherRaProfile_doesNotEarnTheWaiver() throws Exception {
        when(certificateService.getCertificateEntityByFingerprint(any()))
                .thenReturn(inventoryCertificate(UUID.randomUUID()));

        assertFalse(service.authenticateRenewal(renewalRequest(SUBJECT_DN, true)));
    }

    @Test
    void archivedCertificate_isRejected() throws Exception {
        Certificate archived = inventoryCertificate(RA_PROFILE_UUID);
        archived.setArchived(true);
        when(certificateService.getCertificateEntityByFingerprint(any())).thenReturn(archived);

        assertThrows(ScepException.class, () -> service.authenticateRenewal(renewalRequest(SUBJECT_DN, true)));
    }

    @Test
    void subjectDnMismatch_isRejected() throws Exception {
        when(certificateService.getCertificateEntityByFingerprint(any()))
                .thenReturn(inventoryCertificate(RA_PROFILE_UUID));

        assertThrows(ScepException.class, () -> service.authenticateRenewal(renewalRequest("CN=somebody-else", true)));
    }

    private Certificate inventoryCertificate(UUID raProfileUuid) {
        Certificate certificate = new Certificate();
        certificate.setUuid(UUID.randomUUID());
        certificate.setSubjectDn(SUBJECT_DN);
        certificate.setState(CertificateState.ISSUED);
        certificate.setValidationStatus(CertificateValidationStatus.VALID);
        certificate.setRaProfileUuid(raProfileUuid);
        // 10 days left of a 40-day validity: inside the profile's 30-day renewal threshold.
        certificate.setNotBefore(new Date(System.currentTimeMillis() - 30L * 24 * 3600 * 1000));
        certificate.setNotAfter(new Date(System.currentTimeMillis() + 10L * 24 * 3600 * 1000));
        return certificate;
    }

    private ScepRequest renewalRequest(String csrSubjectDn, boolean signatureVerifies) throws Exception {
        ScepRequest request = mock(ScepRequest.class);
        when(request.getSignerCertificate()).thenReturn(clientCertificate);
        when(request.getPkcs10Request()).thenReturn(certificationRequest(csrSubjectDn));
        when(request.verifySignature(any(PublicKey.class))).thenReturn(signatureVerifies);
        return request;
    }

    private JcaPKCS10CertificationRequest certificationRequest(String subjectDn) throws Exception {
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(clientKeyPair.getPrivate());
        return new JcaPKCS10CertificationRequest(
                new JcaPKCS10CertificationRequestBuilder(new X500Name(subjectDn), clientKeyPair.getPublic())
                        .build(signer));
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }

    private static X509Certificate selfSignedCertificate(KeyPair keyPair, String subjectDn) throws Exception {
        X500Name dn = new X500Name(subjectDn);
        Date notBefore = new Date(System.currentTimeMillis() - 60_000L);
        Date notAfter = new Date(System.currentTimeMillis() + 3_600_000L);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                dn, BigInteger.valueOf(System.currentTimeMillis()), notBefore, notAfter, dn, keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(keyPair.getPrivate());
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(builder.build(signer));
    }
}
