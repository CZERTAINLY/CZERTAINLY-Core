package com.czertainly.core.service.cmp.message.handler;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.api.interfaces.core.cmp.error.CmpProcessingException;
import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.connector.cryptography.operations.SignDataResponseDto;
import com.czertainly.api.model.connector.cryptography.operations.data.SignatureResponseData;
import com.czertainly.api.model.core.cmp.CmpProfileVariant;
import com.czertainly.api.interfaces.core.cmp.error.ImplFailureInfo;
import com.czertainly.api.model.core.cmp.ProtectionMethod;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.cryptography.key.KeyState;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.cmp.CmpProfile;
import com.czertainly.core.dao.entity.cmp.CmpTransaction;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.provider.CzertainlyProvider;
import com.czertainly.core.provider.key.CzertainlyPrivateKey;
import com.czertainly.core.service.cmp.configurations.ConfigurationContext;
import com.czertainly.core.service.cmp.configurations.variants.Mobile3gppProfileContext;
import com.czertainly.core.service.cmp.message.CertificateKeyService;
import com.czertainly.core.service.cmp.message.CmpTransactionService;
import com.czertainly.core.service.cmp.mock.CertTestUtil;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class CertConfirmMessageHandlerTest {

    private static final Logger LOG = LoggerFactory.getLogger(CertConfirmMessageHandlerTest.class.getName());

    @InjectMocks
    private CertConfirmMessageHandler tested;

    @Mock private CertificateKeyService certificateKeyService;
    @Mock private CertificateRepository certificateRepository;
    @Mock private CmpTransactionService cmpTransactionService;
    @Mock private CryptographicOperationsApiClient cryptographicOperationsApiClient;

    private X509CertificateHolder x509certificate;
    private final BigInteger serialNumber = BigInteger.valueOf(123456789);
    private CmpProfile cmpProfile;
    private CryptographicKeyItem ckPrivateKey;

    @BeforeAll
    public static void beforeAll() {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    @BeforeEach
    public void setUp() throws Exception {
        KeyPair kp = generateKeyPairEC();
        x509certificate = makeV3Certificate(serialNumber, kp, "CN=Test", kp, "CN=Test");

//        CMPCertificate cmpCert = new CMPCertificate(x509certificate.toASN1Structure());
//        AlgorithmIdentifier digAlg = new DefaultDigestAlgorithmIdentifierFinder().find(x509certificate.getSignatureAlgorithm());
//        if (digAlg == null) {
//            throw new CMPException("cannot find algorithm for digest from signature");
//        }

//        DigestCalculator digester;
//        DigestCalculatorProvider digesterProvider = new JcaDigestCalculatorProviderBuilder()
//                .setProvider(BouncyCastleProvider.PROVIDER_NAME).build();
//        try { digester = digesterProvider.get(digAlg); }
//        catch (OperatorCreationException e) {
//            throw new CMPException("unable to create digest: " + e.getMessage(), e);
//        }
//        derEncodeToStream(cmpCert, digester.getOutputStream());
//        String fingerprint = new DEROctetString(digester.getDigest()).toString();//CertificateUtil.getThumbprint(x509cert.toASN1Structure().getEncoded());

        CryptographicKey key = new CryptographicKey();
        key.setUuid(UUID.randomUUID());
        key.setName("testKey");
        key.setDescription("initial description");
        //key.setTokenInstanceReference(tokenInstanceReference);

        ckPrivateKey = new CryptographicKeyItem();
        ckPrivateKey.setKeyReferenceUuid(UUID.fromString("bb016fae-79b7-4284-9e77-950648dd9d26"));
        ckPrivateKey.setState(KeyState.ACTIVE);
        ckPrivateKey.setType(KeyType.PRIVATE_KEY);
        ckPrivateKey.setFingerprint("7d903217b49fcf947f9b45ba239d4236b99fb75baf7ede08ce53a55c06678f1e");
        ckPrivateKey.setEnabled(true);
        ckPrivateKey.setCryptographicKey(key);
        ckPrivateKey.setKeyAlgorithm(KeyAlgorithm.ECDSA);
        Set<CryptographicKeyItem> items = new HashSet<>();
        items.add((ckPrivateKey));
        key.setItems(items);

        CertificateContent certificateContentSig = new CertificateContent();
        certificateContentSig.setFingerprint("20bfa83ea6a554a92313e62e7f897e71d8fd7406f0a80872defcce755245a63b");
        certificateContentSig.setContent("MIIEcjCCAlqgAwIBAgIUSRfTNEXeaZ+rtyTnaGLwRvzKv40wDQYJKoZIhvcNAQELBQAwQDEgMB4GA1UEAwwXRGVtb0NsaWVudFN1YkNBXzIzMDdSU0ExHDAaBgNVBAoMEzNLZXkgQ29tcGFueSBzLnIuby4wHhcNMjQwNDI5MTMwMTM2WhcNMjYwNDI5MTMwMTM1WjAcMRowGAYDVQQDDBF0ZXN0Y21wY2xpZW50Y2VydDB2MBAGByqGSM49AgEGBSuBBAAiA2IABAuHVkX5et+TLQ5yoHrU2j22IpoDPUFo7c+t01iXFjPXPGf3q5MDwAOp7y79QyXvDgzSo56NTrVzDg9EDSWwjAdVl2fj06QoMLXq7APSc3B15Gvw+pn0ME5Vkfw5T4DcL6OCATQwggEwMAwGA1UdEwEB/wQCMAAwHwYDVR0jBBgwFoAUlW9WiT+pb/3A1zDBt5df5ixKrx0wWAYIKwYBBQUHAQEETDBKMEgGCCsGAQUFBzAChjxodHRwOi8vcGtpLjNrZXkuY29tcGFueS9jYXMvZGVtby9kZW1vY2xpZW50c3ViY2FfMjMwN3JzYS5jcnQwEQYDVR0gBAowCDAGBgRVHSAAMBMGA1UdJQQMMAoGCCsGAQUFBwMCME4GA1UdHwRHMEUwQ6BBoD+GPWh0dHA6Ly9wa2kuM2tleS5jb21wYW55L2NybHMvZGVtby9kZW1vY2xpZW50c3ViY2FfMjMwN3JzYS5jcmwwHQYDVR0OBBYEFGHmdF5qP/gtNmU/iIT4N22jQbiJMA4GA1UdDwEB/wQEAwIFoDANBgkqhkiG9w0BAQsFAAOCAgEA03P8vQpq8wUB6bKpBtS43U+/T/wYUG1tq+2N0G16lZpXgFRAiBOe9ZrUov4iW+gIb8rRNmVcqgaeQtYn57AkC5oQp6tT1FdrEtx71B9EaMaSrGJwLfXypzEYyco4PBmHTadNJimFcIDA25Gp5hAHWDj6GeBUffUOb4PTR+ACBnHi/ApUxRPYCuqeeZZguOIlfy796SWSruCQN+zBQGjKpuCy795eSmaSfjl2h63uOzb+ulLhulHilWi9pk7nGTIbWd1m0LlLrhJQZcesMSlEx7yIkrrz5xCI1/rGu9BnpH5LH1b7TVExtsN3sZmeI10XTlVSLVt0WJTWB71O03QHSi+Fgb28msts2sZ6HSH2zyCxbtvqCZ4aXIfAKh9Cmg5xy6vG9isMtAHCK9m7fKDSnZ57qp6O2Et+zjEbQvOHdu8RHIbQIwHEdAEEUsMDKG7C+DrcZ+2AhK9fm2ToZX3Nt9t3H9BSnFKLfbpsXZsICftrlIFXoVSP+K3/DfLIF8gQQKLxdAiKnUJnGrnMQmy7moBo8LfMkA0MHLlKWwVvUHvNyv5cnhU4J2GSyC8T5aYVD1x/udv+B5xrjSbfbwLsWE2qC6XqThWcypJaSQBM2nzNi78Qnu/HLGsHPfiRBA4wqpG9gZ3qw3BT9nvNI452REqA6WNy4mzEbgpOp0WQkhE=");

        Certificate certificateSig = new Certificate();
        certificateSig.setCertificateContent(certificateContentSig);
        certificateSig.setSerialNumber(new BigInteger(10, new SecureRandom()).toString(16));
        certificateSig.setUuid(UUID.randomUUID());
        certificateSig.setKey(key);

        cmpProfile = new CmpProfile();
        cmpProfile.setUuid(UUID.randomUUID());
        cmpProfile.setName("testCmpProfile");
        cmpProfile.setCreated(LocalDateTime.now());
        cmpProfile.setUpdated(LocalDateTime.now());
        cmpProfile.setEnabled(true);
        cmpProfile.setVariant(CmpProfileVariant.V2_3GPP);
        cmpProfile.setResponseProtectionMethod(ProtectionMethod.SIGNATURE);
        cmpProfile.setSigningCertificate(certificateSig);
    }

    @Test
    public void test_handleOk() throws Exception {
        // -- WHEN --
        String trxId = "999";
        PKIBody body = createBody(x509certificate, serialNumber);
        PKIMessage request = createPkiMessage(
                trxId,
                generateKeyPairEC().getPrivate(),
                body, x509certificate)
                .toASN1Structure();
        ConfigurationContext configuration = new Mobile3gppProfileContext(
                cmpProfile, request, certificateKeyService,
                null, null);

        // -- GIVEN
        given(certificateRepository.findByFingerprint(any()))
                .willReturn(Optional.of(new Certificate()));
        given(cmpTransactionService.findByTransactionIdAndFingerprint(any(), any()))
                .willReturn(Optional.of(new CmpTransaction()));
        given(certificateKeyService.getPrivateKey(any()))
                .willReturn(new CzertainlyPrivateKey(
                        null,
                        ckPrivateKey.getKeyReferenceUuid().toString(),
                        new ConnectorDto(),
                        KeyAlgorithm.ECDSA.getLabel()));
        given(certificateKeyService.getProvider(any())).
                willReturn(CzertainlyProvider.getInstance(cmpProfile.getName(),
                        true, cryptographicOperationsApiClient));
        SignDataResponseDto singData = new SignDataResponseDto();
        SignatureResponseData singDataRsp = new SignatureResponseData();
        singDataRsp.setData("test".getBytes());
        singData.setSignatures(List.of(singDataRsp));
        given(cryptographicOperationsApiClient.signData(any(), any(), any(), any()))
                .willReturn(singData);

        // -- THEN
        PKIMessage response = tested.handle(request, configuration);
        assertEquals(PKIBody.TYPE_CONFIRM, response.getBody().getType());
        assertEquals(new DEROctetString(trxId.getBytes()).toString(),
                response.getHeader().getTransactionID().toString());
        Assertions.assertInstanceOf(PKIConfirmContent.class, response.getBody().getContent());
    }

    @Test
    public void test_handleRelatedCertificateBySerialNumberNotFound() throws Exception {
        // -- WHEN --
        String expectedTrxId = "999";
        PKIBody body = createBody(x509certificate, serialNumber);
        PKIMessage request = createPkiMessage(
                expectedTrxId,
                generateKeyPairEC().getPrivate(),
                body, x509certificate)
                .toASN1Structure();
        ConfigurationContext configuration = new Mobile3gppProfileContext(
                new CmpProfile(), request, certificateKeyService,
                null, null);

        // -- GIVEN
        given(certificateRepository.findByFingerprint(any())).willReturn(Optional.empty());

        // -- THEN
        CmpProcessingException response = assertThrows(
                CmpProcessingException.class, () -> tested.handle(request, configuration));
        assertEquals(response.getImplFailureInfo(), ImplFailureInfo.CMPHANCERTCONF001);
    }

    @Test
    public void test_handleRelatedTransactionNotFound() throws Exception {
        // -- WHEN --
        String trxId = "999";
        PKIBody body = createBody(x509certificate, serialNumber);
        PKIMessage request = createPkiMessage(
                trxId,
                generateKeyPairEC().getPrivate(),
                body, x509certificate)
                .toASN1Structure();
        ConfigurationContext configuration = new Mobile3gppProfileContext(
                new CmpProfile(), request, certificateKeyService,
                null, null);

        // -- GIVEN
        given(certificateRepository.findByFingerprint(any())).willReturn(Optional.of(new Certificate()));

        // -- THEN
        CmpProcessingException response = assertThrows(
                CmpProcessingException.class, () -> tested.handle(request, configuration));
//        assertTrue(response.getMessage().contains(new DEROctetString(Arrays.clone(trxId.getBytes())).toString()));
//        assertTrue(response.getMessage().contains(fingerprint.substring(1)));
        assertEquals(response.getImplFailureInfo(), ImplFailureInfo.CMPHANCERTCONF002);
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
