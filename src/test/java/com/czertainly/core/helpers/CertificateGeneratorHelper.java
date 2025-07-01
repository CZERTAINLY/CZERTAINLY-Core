package com.czertainly.core.helpers;

import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import lombok.Getter;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.ocsp.*;
import org.bouncycastle.cert.ocsp.jcajce.JcaBasicOCSPRespBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Base64;
import java.util.Date;

public class CertificateGeneratorHelper {

    private CertificateGeneratorHelper() {
    }

    public static CertificateChainInfo generateCertificateWithIssuer(KeyAlgorithm algorithm, String issuerDn, String subjectDn, String ocspUrl) throws Exception {
        // Generate self-signed CA
        KeyPair caKeyPair = generateKeyPair(algorithm, null);
        X509Certificate caCert = generateCACertificate(caKeyPair, issuerDn);

        // Generate end-entity certificate
        KeyPair eeKeyPair = generateKeyPair(algorithm, null);
        X509Certificate eeCert = generateEndEntityCertificate(caKeyPair, caCert, eeKeyPair, subjectDn, ocspUrl);

        return new CertificateChainInfo(caKeyPair, caCert, eeKeyPair, eeCert);
    }

    public static KeyPair generateKeyPair(KeyAlgorithm algorithm, AlgorithmParameterSpec params) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(algorithm.getCode());
        keyGen.initialize(params == null ? getDefaultParameterSpec(algorithm) : params);
        return keyGen.generateKeyPair();
    }

    public static X509Certificate generateCACertificate(KeyPair caKeyPair, String subjectDn) throws Exception {
        if (caKeyPair == null) {
            caKeyPair = generateKeyPair(KeyAlgorithm.RSA, null);
        }

        X500Name issuer = new X500Name(subjectDn);
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date start = new Date();
        Date end = new Date(System.currentTimeMillis() + 3650 * 86400000L); // 10 year

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer, serial, start, end, issuer, caKeyPair.getPublic()
        );

        // CA extensions
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .build(caKeyPair.getPrivate());

        return new JcaX509CertificateConverter()
                .getCertificate(builder.build(signer));
    }

    public static X509Certificate generateEndEntityCertificate(
            KeyPair caKeyPair, X509Certificate caCert, KeyPair eeKeyPair, String subjectDn, String ocspUrl) throws Exception {

        X500Name issuer = new X500Name(caCert.getSubjectX500Principal().getName());
        X500Name subject = new X500Name(subjectDn);
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date start = new Date();
        Date end = new Date(System.currentTimeMillis() + 365 * 86400000L); // 1 year

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer, serial, start, end, subject, eeKeyPair.getPublic()
        );

        // Add OCSP AIA extension
        if (ocspUrl != null) {
            GeneralName ocspLocation = new GeneralName(GeneralName.uniformResourceIdentifier, ocspUrl);
            AccessDescription ocspAccess = new AccessDescription(AccessDescription.id_ad_ocsp, ocspLocation);
            AuthorityInformationAccess aia = new AuthorityInformationAccess(ocspAccess);

            builder.addExtension(Extension.authorityInfoAccess, false, aia);
        }

        // Standard extensions
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .build(caKeyPair.getPrivate());

        return new JcaX509CertificateConverter()
                .getCertificate(builder.build(signer));
    }

    public static AlgorithmParameterSpec getDefaultParameterSpec(KeyAlgorithm algorithm) {
        return switch (algorithm) {
            case RSA -> new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4);
            case ECDSA -> null;
            case FALCON -> null;
            case MLDSA -> null;
            case SLHDSA -> null;
            case MLKEM -> null;
            default -> null;
        };
    }

    public static OCSPResp generateOCSPResponse(
            X509Certificate issuerCert,
            PrivateKey issuerKey,
            X509Certificate certToCheck,
            CertificateStatus status
    ) throws Exception {
        // Digest calculator for CertificateID
        DigestCalculatorProvider digCalcProv = new JcaDigestCalculatorProviderBuilder().build();
        DigestCalculator digCalc = digCalcProv.get(CertificateID.HASH_SHA1);

        // Create CertificateID for the cert to check
        CertificateID certId = new CertificateID(digCalc, new JcaX509CertificateHolder(issuerCert), certToCheck.getSerialNumber());

        // Build OCSP response
        BasicOCSPRespBuilder respBuilder = new JcaBasicOCSPRespBuilder(issuerCert.getPublicKey(), digCalc);

        // Add response for the certificate
        respBuilder.addResponse(certId, status);

        // Sign the OCSP response
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(issuerKey);

        X509CertificateHolder[] chain = { new JcaX509CertificateHolder(issuerCert) };
        BasicOCSPResp basicResp = respBuilder.build(signer, chain, new Date());

        // Wrap in OCSPResp
        OCSPRespBuilder ocspRespBuilder = new OCSPRespBuilder();
        return ocspRespBuilder.build(OCSPRespBuilder.SUCCESSFUL, basicResp);
    }

    @Getter
    public static class CertificateChainInfo {
        private final KeyPair caCertificateKeyPair;
        private final X509Certificate caCertificate;

        private final KeyPair endEntityCertificateKeyPair;
        private final X509Certificate endEntityCertificate;

        public String getCaCertificateBase64Encoded() throws CertificateEncodingException {
            return Base64.getEncoder().encodeToString(caCertificate.getEncoded());
        }

        public String getEndEntityCertificateBase64Encoded() throws CertificateEncodingException {
            return Base64.getEncoder().encodeToString(endEntityCertificate.getEncoded());
        }

        public CertificateChainInfo(KeyPair caCertificateKeyPair, X509Certificate caCertificate, KeyPair endEntityCertificateKeyPair, X509Certificate endEntityCertificate) {
            this.caCertificateKeyPair = caCertificateKeyPair;
            this.caCertificate = caCertificate;
            this.endEntityCertificateKeyPair = endEntityCertificateKeyPair;
            this.endEntityCertificate = endEntityCertificate;
        }
    }
}
