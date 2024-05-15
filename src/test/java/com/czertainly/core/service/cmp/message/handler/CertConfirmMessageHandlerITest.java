package com.czertainly.core.service.cmp.message.handler;

import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.cmp.CmpProfileVariant;
import com.czertainly.api.model.core.cmp.CmpTransactionState;
import com.czertainly.api.model.core.cmp.ProtectionMethod;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.cryptography.key.KeyState;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.cmp.CmpProfile;
import com.czertainly.core.dao.entity.cmp.CmpTransaction;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.dao.repository.cmp.CmpProfileRepository;
import com.czertainly.core.provider.CzertainlyProvider;
import com.czertainly.core.service.cmp.configurations.ConfigurationContext;
import com.czertainly.core.service.cmp.configurations.variants.CmpConfigurationContext;
import com.czertainly.core.service.cmp.configurations.variants.Mobile3gppProfileContext;
import com.czertainly.core.service.cmp.message.CertificateKeyServiceImpl;
import com.czertainly.core.service.cmp.message.CmpTransactionService;
import com.czertainly.core.service.cmp.mock.CertTestUtil;
import com.czertainly.core.util.BaseSpringBootTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.cmp.CMPCertificate;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.cmp.PKIConfirmContent;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.cmp.*;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.*;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Arrays;
import org.junit.Ignore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Ignore("This test is not working because of CzertainlyProvider")
public class CertConfirmMessageHandlerITest extends BaseSpringBootTest {

    private static final Logger LOG = LoggerFactory.getLogger(CertConfirmMessageHandlerITest.class.getName());

    @Autowired private CertificateContentRepository certificateContentRepository;
    @Autowired private CertificateRepository certificateRepository;
    @Autowired private CmpTransactionService cmpTransactionService;
    @Autowired private CmpProfileRepository cmpProfileRepository;
    @Autowired private RaProfileRepository raProfileRepository;
    @Autowired private CertificateKeyServiceImpl certificateKeyService;
    @Autowired private CryptographicKeyRepository cryptographicKeyRepository;
    @Autowired private CryptographicKeyItemRepository cryptographicKeyItemRepository;
    @Autowired private TokenInstanceReferenceRepository tokenInstanceReferenceRepository;
    @Autowired private ConnectorRepository connectorRepository;

    private static final String RA_PROFILE_NAME = "testRaProfile0";
    private Certificate certificate;
    private CertConfirmMessageHandler tested;
    private CmpProfile cmpProfile;
    private CzertainlyProvider provider;

    private X509CertificateHolder x509cert;
    private BigInteger SERIAL_NUMBER = BigInteger.valueOf(123456789);
    private String FINGERPRINT;
    private CryptographicKey key;
    private WireMockServer mockServer;

    @BeforeEach
    public void setUp() throws Exception {
        mockServer = new WireMockServer(0);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());
        // -- GIVEN --
        tested = new CertConfirmMessageHandler();
        tested.setCertificateRepository(certificateRepository);
        tested.setCmpTransactionService(cmpTransactionService);

        CertificateContent certificateContentSig = new CertificateContent();
        certificateContentSig.setFingerprint("20bfa83ea6a554a92313e62e7f897e71d8fd7406f0a80872defcce755245a63b");
        certificateContentSig.setContent("MIIEcjCCAlqgAwIBAgIUSRfTNEXeaZ+rtyTnaGLwRvzKv40wDQYJKoZIhvcNAQELBQAwQDEgMB4GA1UEAwwXRGVtb0NsaWVudFN1YkNBXzIzMDdSU0ExHDAaBgNVBAoMEzNLZXkgQ29tcGFueSBzLnIuby4wHhcNMjQwNDI5MTMwMTM2WhcNMjYwNDI5MTMwMTM1WjAcMRowGAYDVQQDDBF0ZXN0Y21wY2xpZW50Y2VydDB2MBAGByqGSM49AgEGBSuBBAAiA2IABAuHVkX5et+TLQ5yoHrU2j22IpoDPUFo7c+t01iXFjPXPGf3q5MDwAOp7y79QyXvDgzSo56NTrVzDg9EDSWwjAdVl2fj06QoMLXq7APSc3B15Gvw+pn0ME5Vkfw5T4DcL6OCATQwggEwMAwGA1UdEwEB/wQCMAAwHwYDVR0jBBgwFoAUlW9WiT+pb/3A1zDBt5df5ixKrx0wWAYIKwYBBQUHAQEETDBKMEgGCCsGAQUFBzAChjxodHRwOi8vcGtpLjNrZXkuY29tcGFueS9jYXMvZGVtby9kZW1vY2xpZW50c3ViY2FfMjMwN3JzYS5jcnQwEQYDVR0gBAowCDAGBgRVHSAAMBMGA1UdJQQMMAoGCCsGAQUFBwMCME4GA1UdHwRHMEUwQ6BBoD+GPWh0dHA6Ly9wa2kuM2tleS5jb21wYW55L2NybHMvZGVtby9kZW1vY2xpZW50c3ViY2FfMjMwN3JzYS5jcmwwHQYDVR0OBBYEFGHmdF5qP/gtNmU/iIT4N22jQbiJMA4GA1UdDwEB/wQEAwIFoDANBgkqhkiG9w0BAQsFAAOCAgEA03P8vQpq8wUB6bKpBtS43U+/T/wYUG1tq+2N0G16lZpXgFRAiBOe9ZrUov4iW+gIb8rRNmVcqgaeQtYn57AkC5oQp6tT1FdrEtx71B9EaMaSrGJwLfXypzEYyco4PBmHTadNJimFcIDA25Gp5hAHWDj6GeBUffUOb4PTR+ACBnHi/ApUxRPYCuqeeZZguOIlfy796SWSruCQN+zBQGjKpuCy795eSmaSfjl2h63uOzb+ulLhulHilWi9pk7nGTIbWd1m0LlLrhJQZcesMSlEx7yIkrrz5xCI1/rGu9BnpH5LH1b7TVExtsN3sZmeI10XTlVSLVt0WJTWB71O03QHSi+Fgb28msts2sZ6HSH2zyCxbtvqCZ4aXIfAKh9Cmg5xy6vG9isMtAHCK9m7fKDSnZ57qp6O2Et+zjEbQvOHdu8RHIbQIwHEdAEEUsMDKG7C+DrcZ+2AhK9fm2ToZX3Nt9t3H9BSnFKLfbpsXZsICftrlIFXoVSP+K3/DfLIF8gQQKLxdAiKnUJnGrnMQmy7moBo8LfMkA0MHLlKWwVvUHvNyv5cnhU4J2GSyC8T5aYVD1x/udv+B5xrjSbfbwLsWE2qC6XqThWcypJaSQBM2nzNi78Qnu/HLGsHPfiRBA4wqpG9gZ3qw3BT9nvNI452REqA6WNy4mzEbgpOp0WQkhE=");
        certificateContentSig = certificateContentRepository.save(certificateContentSig);

        Connector connector = new Connector();
        connector.setUrl("http://localhost:"+mockServer.port());
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        TokenInstanceReference tokenInstanceReference = new TokenInstanceReference();
        tokenInstanceReference.setTokenInstanceUuid("1l");
        tokenInstanceReference.setConnector(connector);
        tokenInstanceReferenceRepository.save(tokenInstanceReference);

        key = new CryptographicKey();
        key.setUuid(UUID.randomUUID());
        key.setName("testKey");
        key.setDescription("initial description");
        key.setTokenInstanceReference(tokenInstanceReference);
        key = cryptographicKeyRepository.save(key);

        CryptographicKeyItem ckPrivateKey = new CryptographicKeyItem();
        ckPrivateKey.setKeyReferenceUuid(UUID.fromString("bb016fae-79b7-4284-9e77-950648dd9d26"));
        ckPrivateKey.setState(KeyState.ACTIVE);
        ckPrivateKey.setType(KeyType.PRIVATE_KEY);
        ckPrivateKey.setFingerprint("7d903217b49fcf947f9b45ba239d4236b99fb75baf7ede08ce53a55c06678f1e");
        ckPrivateKey.setEnabled(true);
        ckPrivateKey.setCryptographicKey(key);
        ckPrivateKey.setKeyAlgorithm(KeyAlgorithm.ECDSA);
        cryptographicKeyItemRepository.save(ckPrivateKey);
        Set<CryptographicKeyItem> items = new HashSet<>();
        items.add((ckPrivateKey));
        key.setItems(items);
        key = cryptographicKeyRepository.save(key);
        cryptographicKeyRepository.flush();

        Certificate certificateSig = new Certificate();
        certificateSig.setCertificateContent(certificateContentSig);
        certificateSig.setSerialNumber(new BigInteger(10, new SecureRandom()).toString(16));
        certificateSig.setUuid(UUID.randomUUID());
        certificateSig.setKey(key);
        certificateSig.setKeyUuid(key.getUuid());
        certificateSig = certificateRepository.save(certificateSig);
        certificateContentSig.setCertificate(certificateSig);
        certificateContentRepository.save(certificateContentSig);

        RaProfile raProfile = new RaProfile();
        raProfile.setName(RA_PROFILE_NAME);
        raProfile = raProfileRepository.save(raProfile);

        cmpProfile = new CmpProfile();
        cmpProfile.setUuid(UUID.randomUUID());
        cmpProfile.setName("testCmpProfile");
        cmpProfile.setCreated(LocalDateTime.now());
        cmpProfile.setUpdated(LocalDateTime.now());
        cmpProfile.setEnabled(true);
        cmpProfile.setVariant(CmpProfileVariant.V2_3GPP);
        cmpProfile.setRaProfile(raProfile);
        cmpProfile.setResponseProtectionMethod(ProtectionMethod.SIGNATURE);
        cmpProfile.setSigningCertificate(certificateSig);
        cmpProfile = cmpProfileRepository.save(cmpProfile);

        CertificateContent certificateContent = new CertificateContent();
        certificateContent = certificateContentRepository.save(certificateContent);

        //X509Certificate x509Cert = CertificateUtil.parseCertificate(certificateData);
        //String fingerprint = CertificateUtil.getThumbprint(certificate.getEncoded());

        KeyPair kp = generateKeyPairEC();
        x509cert = makeV3Certificate(SERIAL_NUMBER, kp, "CN=Test", kp, "CN=Test");

        CMPCertificate cmpCert = new CMPCertificate(x509cert.toASN1Structure());
        AlgorithmIdentifier digAlg = new DefaultDigestAlgorithmIdentifierFinder().find((AlgorithmIdentifier) x509cert.getSignatureAlgorithm());
        if (digAlg == null) {
            throw new CMPException("cannot find algorithm for digest from signature");
        }

        DigestCalculator digester;
        DigestCalculatorProvider digesterProvider = new JcaDigestCalculatorProviderBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME).build();
        try { digester = digesterProvider.get(digAlg); }
        catch (OperatorCreationException e) {
            throw new CMPException("unable to create digest: " + e.getMessage(), e);
        }
        derEncodeToStream(cmpCert, digester.getOutputStream());
        FINGERPRINT = new DEROctetString(digester.getDigest()).toString();//CertificateUtil.getThumbprint(x509cert.toASN1Structure().getEncoded());
        LOG.info("FP={}", FINGERPRINT);

        certificate = new Certificate();
        certificate.setCertificateContent(certificateContent);
        certificate.setSerialNumber(SERIAL_NUMBER.toString(16));
        certificate.setFingerprint(FINGERPRINT.substring(1)/*remove ''#'*/);
        certificate.setUuid(UUID.randomUUID());
        certificate = certificateRepository.save(certificate);

        CmpTransaction cmpTransaction = new CmpTransaction();
        cmpTransaction.setTransactionId(new DEROctetString(Arrays.clone("999".getBytes())).toString());
        cmpTransaction.setCmpProfile(cmpProfile);
        cmpTransaction.setCertificateUuid(certificate.getUuid());
        cmpTransaction.setState(CmpTransactionState.CERT_ISSUED);
        cmpTransactionService.save(cmpTransaction);

        provider = certificateKeyService.getProvider(cmpProfile.getName());
    }

    @AfterEach
    public void tearDown() {
        mockServer.stop();
    }

    @Test
    public void testHandle() throws Exception {
        String expectedTrxId = "999";
        // -- WHEN --
        PKIBody body = createBody(x509cert, SERIAL_NUMBER);
        PKIMessage request = createPkiMessage(
                expectedTrxId,
                generateKeyPairEC().getPrivate(),
                body, x509cert)
                .toASN1Structure();
        PKIMessage response = tested.handle(request,
                new Mobile3gppProfileContext(cmpProfile,
                        request,
                        certificateKeyService,
                        null,
                        null));
        // -- THEN --
        // (1) check structure
        assertEquals(PKIBody.TYPE_CONFIRM, response.getBody().getType());
        assertEquals(new DEROctetString(expectedTrxId.getBytes()).toString(),
                response.getHeader().getTransactionID().toString());
        Assertions.assertInstanceOf(PKIConfirmContent.class, response.getBody().getContent());

        // (2) check certificate and its state
        String serialNumber = SERIAL_NUMBER.toString(16);
        Certificate certificate = certificateRepository.findBySerialNumberIgnoreCase(serialNumber).get();
        assertEquals(CertificateState.ISSUED, certificate.getState());
        // (3) check transaction and its state
        cmpTransactionService.findByTransactionIdAndCertificateSerialNumber(expectedTrxId, serialNumber)
                .ifPresent(cmpTransaction -> {
                assertEquals(CmpTransactionState.CERT_CONFIRMED, cmpTransaction.getState());
            }
        );
    }

    private PKIBody createBody(X509CertificateHolder cert, BigInteger certReqId) throws
            OperatorCreationException, CMPException {
        CertificateConfirmationContent content = new CertificateConfirmationContentBuilder()
                .addAcceptedCertificate(cert, certReqId)
                .build(new JcaDigestCalculatorProviderBuilder()
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME).build());
        return new PKIBody(PKIBody.TYPE_CERT_CONFIRM, content.toASN1Structure());
    }

    private ProtectedPKIMessage createPkiMessage(String txdId,
                                                 PrivateKey privateKey,
                                                 PKIBody body, X509CertificateHolder cert)
            throws Exception {
        X500Name issuerDN = new X500Name("CN=ManagementCA");
        X500Name userDN = new X500Name("CN=user");
        byte[] senderNonce = "12345".getBytes();
        byte[] transactionId = txdId.getBytes();
        GeneralName sender = new GeneralName(userDN);
        GeneralName recipient = new GeneralName(issuerDN);

        ContentSigner msgSigner = new JcaContentSignerBuilder(CertTestUtil.getSigningAlgNameFromKeyAlg(privateKey.getAlgorithm()))//
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(privateKey);
        return new ProtectedPKIMessageBuilder(sender, recipient)
                .setMessageTime(new Date())
                .setSenderNonce(senderNonce)
                .setTransactionID(transactionId)
                .setBody(body)
                .addCMPCertificate(cert)
                .build(msgSigner);
    }

    private static KeyPair generateKeyPairEC() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, NoSuchProviderException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME); // Initialize to generate asymmetric keys to be used with one of the Elliptic Curve algorithms
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp384r1"); // using domain parameters specified by safe curve spec of secp384r1
        keyPairGenerator.initialize(ecSpec, new SecureRandom("_n3coHodn@Kryptickeho!".getBytes()));
        return keyPairGenerator.generateKeyPair(); // Generate asymmetric keys.
    }

    private static X509CertificateHolder makeV3Certificate(BigInteger serialNumber, KeyPair subKP, String _subDN, KeyPair issKP,
                                                           String _issDN) throws OperatorCreationException {

        PublicKey subPub = subKP.getPublic();
        PrivateKey issPriv = issKP.getPrivate();
        PublicKey issPub = issKP.getPublic();

        X509v3CertificateBuilder v1CertGen = new JcaX509v3CertificateBuilder(
                new X500Name(_issDN),
                serialNumber,
                new Date(System.currentTimeMillis()),
                new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 100)),
                new X500Name(_subDN),
                subPub);

        ContentSigner signer = new JcaContentSignerBuilder("SHA384withECDSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(issPriv);

        X509CertificateHolder certHolder = v1CertGen.build(signer);

        ContentVerifierProvider verifier = new JcaContentVerifierProviderBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(issPub);

        //assertTrue(certHolder.isSignatureValid(verifier));

        return certHolder;
    }

    static void derEncodeToStream(ASN1Object obj, OutputStream stream)
    {
        try
        {
            obj.encodeTo(stream, ASN1Encoding.DER);
            stream.close();
        }
        catch (IOException e)
        {
            throw new CMPRuntimeException("unable to DER encode object: " + e.getMessage(), e);
        }
    }
}
