package com.czertainly.core.service.cmp.message.handler;

import com.czertainly.api.model.connector.cryptography.operations.SignDataResponseDto;
import com.czertainly.api.model.connector.cryptography.operations.data.SignatureResponseData;
import com.czertainly.core.service.cmp.mock.CertTestUtil;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Json;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cmp.CMPCertificate;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.X509DefaultEntryConverter;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.asn1.x509.X509NameEntryConverter;
import org.bouncycastle.cert.CertException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.cmp.*;
import org.bouncycastle.cert.crmf.*;
import org.bouncycastle.cert.crmf.jcajce.JcePKMACValuesCalculator;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.*;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CmpTestUtil {

    public static WireMockServer createSigningPlatform() {
        // prepare mock server - for cryptographic provider
        WireMockServer mockServer = new WireMockServer(0);
        mockServer.start();
        WireMock.configureFor("localhost", mockServer.port());

        // -- if there is a need something to sign (mock server is called)
        // see https://docs.czertainly.com/api/core-cryptographic-operations/#tag/Cryptographic-Operations-Controller/operation/signData
        // see https://docs.czertainly.com/api/connector-cryptography-provider/#tag/Cryptographic-Operations/operation/signData
        SignDataResponseDto singDataRsp = new SignDataResponseDto();
        SignatureResponseData singedDataAsBytes = new SignatureResponseData();
        singedDataAsBytes.setData("test".getBytes());
        singDataRsp.setSignatures(List.of(singedDataAsBytes));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/cryptographyProvider/tokens/[^/]+/keys/[^/]+/sign"))
                .willReturn(WireMock.okJson(Json.write(singDataRsp))));
        return mockServer;
    }

    // -- pki message api
    public static ProtectedPKIMessage createSignatureBasedMessage(String transactionId,
                                                                  PrivateKey privateKey,
                                                                  PKIBody body)
            throws Exception {
        String signAlgorithmName = CertTestUtil.getSigningAlgNameFromKeyAlg(privateKey.getAlgorithm());
        ContentSigner msgSigner = new JcaContentSignerBuilder(signAlgorithmName)//
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(privateKey);
        return createPkiMessageBuilder(transactionId, body)
                .build(msgSigner);
    }

    public static ProtectedPKIMessage createMacBasedMessage(String transactionId,
                                                      String sharedSecret, PKIBody body)
            throws CRMFException, CMPException {
        JcePKMACValuesCalculator jcePkmacCalc = new JcePKMACValuesCalculator();
        jcePkmacCalc.setup(
                new AlgorithmIdentifier(new ASN1ObjectIdentifier("1.3.14.3.2.26")), // SHA1
                new AlgorithmIdentifier(new ASN1ObjectIdentifier("1.2.840.113549.2.7"))); // HMAC/SHA1
        MacCalculator macCalculator = new PKMACBuilder(jcePkmacCalc)
                .setIterationCount(1000)
                .setSaltLength(25)
                //.setParameters(new PBMParameter(salt, owf, iterCount, macAlg))
                .build(sharedSecret.toCharArray());
        return createPkiMessageBuilder(transactionId, body)
                .build(macCalculator);
    }

    public static ProtectedPKIMessageBuilder createPkiMessageBuilder(
            String transactionId, PKIBody body) {
        X500Name issuerDN = new X500Name("CN=ManagementCA");
        X500Name userDN = new X500Name("CN=user");
        byte[] senderNonce = "12345".getBytes();
        GeneralName sender = new GeneralName(userDN);
        GeneralName recipient = new GeneralName(issuerDN);

        return new ProtectedPKIMessageBuilder(sender, recipient)
                .setMessageTime(new Date())
                .setSenderNonce(senderNonce)
                .setTransactionID(transactionId.getBytes())
                .setBody(body);
    }

    public static PKIBody createCertConfBody(X509CertificateHolder cert, BigInteger certReqId) throws
            OperatorCreationException, CMPException {
        CertificateConfirmationContent content = new CertificateConfirmationContentBuilder()
                .addAcceptedCertificate(cert, certReqId)
                .build(new JcaDigestCalculatorProviderBuilder()
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME).build());
        return new PKIBody(PKIBody.TYPE_CERT_CONFIRM, content.toASN1Structure());
    }

    // -- crypto objects
    public static KeyPair generateKeyPairEC() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, NoSuchProviderException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME); // Initialize to generate asymmetric keys to be used with one of the Elliptic Curve algorithms
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp384r1"); // using domain parameters specified by safe curve spec of secp384r1
        keyPairGenerator.initialize(ecSpec, new SecureRandom("_n3coHodn@Kryptickeho!".getBytes()));
        return keyPairGenerator.generateKeyPair(); // Generate asymmetric keys.
    }

    public static X509CertificateHolder makeV3Certificate(BigInteger serialNumber, KeyPair subKP,
                                                           String _subDN, KeyPair issKP,
                                                           String _issDN) throws OperatorCreationException, CertException {

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

        assertTrue(certHolder.isSignatureValid(verifier));

        return certHolder;
    }

    public static void derEncodeToStream(ASN1Object obj, OutputStream stream) {
        try {
            obj.encodeTo(stream, ASN1Encoding.DER);
            stream.close();
        } catch (IOException e) {
            throw new CMPRuntimeException("unable to DER encode object: " + e.getMessage(), e);
        }
    }

    public static DigestCalculator createMessageDigest(X509CertificateHolder x509certificate)
            throws CMPException, OperatorCreationException {
        CMPCertificate cmpCert = new CMPCertificate(x509certificate.toASN1Structure());
        AlgorithmIdentifier digAlg = new DefaultDigestAlgorithmIdentifierFinder().find(x509certificate.getSignatureAlgorithm());
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
        return digester;
    }

    public static PKIBody createCrmfBody(String transactionId,
            X509CertificateHolder x509certificate,  KeyPair keyPair, long certReqId)
            throws IOException, CRMFException, OperatorCreationException {
        CertificateRequestMessageBuilder msgbuilder = new CertificateRequestMessageBuilder(BigInteger.valueOf(certReqId));
        X509NameEntryConverter dnconverter = new X509DefaultEntryConverter();
        X500Name issuerDN = X500Name.getInstance(new org.bouncycastle.asn1.x509.X509Name("CN=ManagementCA").toASN1Primitive());
        X500Name subjectDN = X500Name.getInstance(new X509Name("CN=user", dnconverter).toASN1Primitive());
        msgbuilder.setIssuer(issuerDN);
        msgbuilder.setSubject(subjectDN);
        final byte[]                  bytes = keyPair.getPublic().getEncoded();
        final ByteArrayInputStream bIn = new ByteArrayInputStream(bytes);
        final ASN1InputStream dIn = new ASN1InputStream(bIn);
        final SubjectPublicKeyInfo keyInfo = new SubjectPublicKeyInfo((org.bouncycastle.asn1.ASN1Sequence)dIn.readObject());
        msgbuilder.setPublicKey(keyInfo);
        GeneralName sender = new GeneralName(subjectDN);
        msgbuilder.setAuthInfoSender(sender);
        Control control = new RegTokenControl("foo123");
        msgbuilder.addControl(control);
        Provider prov = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        ContentSigner popsigner = new JcaContentSignerBuilder("SHA256withECDSA").setProvider(prov).build(keyPair.getPrivate());
        msgbuilder.setProofOfPossessionSigningKeySigner(popsigner);
        CertificateRequestMessage msg = msgbuilder.build();
        org.bouncycastle.asn1.crmf.CertReqMessages msgs = new org.bouncycastle.asn1.crmf.CertReqMessages(msg.toASN1Structure());
        org.bouncycastle.asn1.cmp.PKIBody pkibody = new org.bouncycastle.asn1.cmp.PKIBody(org.bouncycastle.asn1.cmp.PKIBody.TYPE_INIT_REQ, msgs);
        return pkibody;
    }
}
