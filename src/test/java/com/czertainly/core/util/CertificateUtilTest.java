package com.czertainly.core.util;

import com.czertainly.core.dao.entity.Certificate;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;

import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CertificateUtilTest {

    private static final String VALID_SAN_STRING = "{\"dNSName\":[\"czertainly.com\"],\"directoryName\":[],\"ediPartyName\":[],\"iPAddress\":[\"192.168.10.10\"],\"otherName\":[\"1.2.3.4=example othername\"],\"registeredID\":[],\"rfc822Name\":[],\"uniformResourceIdentifier\":[],\"x400Address\":[]}";

    private static final Map<String, List<String>> VALID_SAN_MAP = Map.of(
            "registeredID", List.of(),
            "ediPartyName", List.of(),
            "iPAddress", List.of("192.168.10.10"),
            "x400Address", List.of(),
            "rfc822Name", List.of(),
            "otherName", List.of("1.2.3.4=example othername"),
            "dNSName", List.of("czertainly.com"),
            "uniformResourceIdentifier", List.of(),
            "directoryName", List.of()
    );

    @Test
    void testSerializeSans() {
        String result = CertificateUtil.serializeSans(VALID_SAN_MAP);
        Assertions.assertEquals(VALID_SAN_STRING, result);

        String nullResult = CertificateUtil.serializeSans(null);
        Assertions.assertEquals("{}", nullResult);

        String emptyResult = CertificateUtil.serializeSans(new HashMap<>());
        Assertions.assertEquals("{}", emptyResult);
    }

    @Test
    void testDeserializeSans() {
        Map<String, List<String>> result = CertificateUtil.deserializeSans(VALID_SAN_STRING);
        Assertions.assertEquals(VALID_SAN_MAP, result);

        Map<String, List<String>> emptyResult = CertificateUtil.deserializeSans(null);
        Assertions.assertTrue(emptyResult.isEmpty());

        Map<String, List<String>> emptyStringResult = CertificateUtil.deserializeSans("");
        Assertions.assertTrue(emptyStringResult.isEmpty());
    }

    @Test
    void testInvalidDeserializeSans() {
        String invalidJson = "{invalid json}";
        Assertions.assertThrows(IllegalStateException.class, () -> CertificateUtil.deserializeSans(invalidJson));
    }

    @Test
    void testInvalidSerializeSans() {
        Map<String, List<String>> invalidMap = new HashMap<>();
        invalidMap.put("invalidKey", null);

        Assertions.assertThrows(IllegalStateException.class, () -> CertificateUtil.serializeSans(invalidMap));
    }

    @Test
    void testParseHybridCertificate() throws IOException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, SignatureException, OperatorCreationException, CertificateException {
        X509Certificate certificate = createHybridCertificate();

        Certificate certificateEntity = new Certificate();
        CertificateUtil.prepareIssuedCertificate(certificateEntity, certificate);
        Assertions.assertTrue(certificateEntity.isHybridCertificate());
        Assertions.assertEquals("ML-DSA-44", certificateEntity.getAltSignatureAlgorithm());

    }

    public static X509Certificate createHybridCertificate() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, IOException, InvalidKeyException, SignatureException, OperatorCreationException, CertificateException {
        Provider provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (provider == null) {
            provider = new BouncyCastleProvider();
            Security.addProvider(provider);
        }

        KeyPairGenerator defaultKeyGen = KeyPairGenerator.getInstance("RSA");
        defaultKeyGen.initialize(2048);
        KeyPair defaultKeyPair = defaultKeyGen.generateKeyPair();
        Date notBefore = new Date();
        Date notAfter = new Date(Long.MAX_VALUE);
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                new X500Name("CN=issuer"), BigInteger.ONE, notBefore, notAfter, new X500Name("CN=subject"), defaultKeyPair.getPublic());

        KeyPairGenerator altKeyGen = KeyPairGenerator.getInstance("ML-DSA");
        altKeyGen.initialize(MLDSAParameterSpec.ml_dsa_44);
        KeyPair alternativeKeyPair = altKeyGen.generateKeyPair();

        ExtensionsGenerator extensionsGenerator = new ExtensionsGenerator();
        SubjectAltPublicKeyInfo subjectAltPublicKeyInfo = SubjectAltPublicKeyInfo.getInstance(
                ASN1Sequence.getInstance(alternativeKeyPair.getPublic().getEncoded()));
        certBuilder.addExtension(Extension.subjectAltPublicKeyInfo, false, subjectAltPublicKeyInfo);
        extensionsGenerator.addExtension(Extension.subjectAltPublicKeyInfo, false, subjectAltPublicKeyInfo);

        AlgorithmIdentifier altSignatureAlgorithm = new AlgorithmIdentifier(NISTObjectIdentifiers.id_ml_dsa_44);
        AltSignatureAlgorithm altSignatureAlgorithm1 = new AltSignatureAlgorithm(altSignatureAlgorithm);
        certBuilder.addExtension(Extension.altSignatureAlgorithm, false, altSignatureAlgorithm1);
        extensionsGenerator.addExtension(Extension.altSignatureAlgorithm, false, altSignatureAlgorithm1);

        V3TBSCertificateGenerator tbsCertificateGenerator = new V3TBSCertificateGenerator();
        tbsCertificateGenerator.setIssuer(new X500Name("CN=issuer"));
        tbsCertificateGenerator.setSerialNumber(new ASN1Integer(BigInteger.ONE));
        tbsCertificateGenerator.setEndDate(new Time(notAfter));
        tbsCertificateGenerator.setStartDate(new Time(notBefore));
        tbsCertificateGenerator.setSubject(new X500Name("CN=subject"));
        tbsCertificateGenerator.setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(defaultKeyPair.getPublic().getEncoded()));
        tbsCertificateGenerator.setSignature(new AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption));
        tbsCertificateGenerator.setExtensions(extensionsGenerator.generate());
        TBSCertificate tbsCertificate = tbsCertificateGenerator.generateTBSCertificate();

        Signature signature = Signature.getInstance("ML-DSA");
        signature.initSign(alternativeKeyPair.getPrivate());
        signature.update(tbsCertificate.getEncoded());
        byte[] signedData = signature.sign();
        AltSignatureValue altSignatureValue = new AltSignatureValue(signedData);
        certBuilder.addExtension(Extension.altSignatureValue, false, altSignatureValue);

        JcaX509CertificateConverter converter = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(defaultKeyPair.getPrivate());
        return converter.getCertificate(certBuilder.build(signer));
    }

}