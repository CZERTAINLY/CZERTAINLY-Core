package com.czertainly.core.util;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.connector.cryptography.enums.CryptographicAlgorithm;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.core.dao.entity.Certificate;
import jakarta.xml.bind.DatatypeConverter;
import org.bouncycastle.asn1.pkcs.Attribute;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.jcajce.provider.asymmetric.x509.CertificateFactory;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.DefaultAlgorithmNameFinder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.*;

public class CertificateUtil {

    public static final Map<String, String> CERTIFICATE_ALGORITHM_FROM_PROVIDER = new HashMap<>();
    public static final Map<String, String> CERTIFICATE_ALGORITHM_FRIENDLY_NAME = new HashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(CertificateUtil.class);
    @SuppressWarnings("serial")
    private static final Map<String, String> SAN_TYPE_MAP = new HashMap<>();
    @SuppressWarnings("serial")
    private static final List<String> KEY_USAGE_LIST = Arrays.asList("digitalSignature", "nonRepudiation", "keyEncipherment", "dataEncipherment", "keyAgreement", "keyCertSign", "cRLSign", "encipherOnly", "decipherOnly");

    static {
        SAN_TYPE_MAP.put("0", "otherName");
        SAN_TYPE_MAP.put("1", "rfc822Name");
        SAN_TYPE_MAP.put("2", "dNSName");
        SAN_TYPE_MAP.put("3", "x400Address");
        SAN_TYPE_MAP.put("4", "directoryName");
        SAN_TYPE_MAP.put("5", "ediPartyName");
        SAN_TYPE_MAP.put("6", "uniformResourceIdentifier");
        SAN_TYPE_MAP.put("7", "iPAddress");
        SAN_TYPE_MAP.put("8", "registeredID");
    }

    static {
        CERTIFICATE_ALGORITHM_FRIENDLY_NAME.put("SPHINCSPLUS", CryptographicAlgorithm.SPHINCSPLUS.getCode());
        CERTIFICATE_ALGORITHM_FRIENDLY_NAME.put("DILITHIUM", CryptographicAlgorithm.DILITHIUM.getCode());
    }

    static {
        CERTIFICATE_ALGORITHM_FROM_PROVIDER.put("EC", CryptographicAlgorithm.ECDSA.toString());
        CERTIFICATE_ALGORITHM_FROM_PROVIDER.put("SPHINCS+", CryptographicAlgorithm.SPHINCSPLUS.toString());
        CERTIFICATE_ALGORITHM_FROM_PROVIDER.put("Dilithium", CryptographicAlgorithm.DILITHIUM.toString());
        CERTIFICATE_ALGORITHM_FROM_PROVIDER.put("Falcon", CryptographicAlgorithm.FALCON.toString());
    }

    private CertificateUtil() {
    }

    public static X509Certificate getX509Certificate(byte[] decoded) throws CertificateException {
        try {
            X509Certificate certificate = (X509Certificate) new CertificateFactory().engineGenerateCertificate(new ByteArrayInputStream(decoded));
            if (certificate.getPublicKey() == null) {
                java.security.cert.CertificateFactory certificateFactory = java.security.cert.CertificateFactory.getInstance("x.509");
                return (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(decoded));
            }
            return certificate;
        } catch (Exception e) {
            X509Certificate certificate = (X509Certificate) new CertificateFactory().engineGenerateCertificates(new ByteArrayInputStream(decoded)).iterator().next();
            if (certificate.getPublicKey() == null) {
                java.security.cert.CertificateFactory certificateFactory = java.security.cert.CertificateFactory.getInstance("x.509");
                return (X509Certificate) certificateFactory.generateCertificates(new ByteArrayInputStream(decoded)).iterator().next();
            }
            return certificate;
        }
    }

    public static String getDnFromX509Certificate(String certInBase64) throws CertificateException, NotFoundException {
        return getDnFromX509Certificate(getX509Certificate(certInBase64));
    }

    public static String getDnFromX509Certificate(X509Certificate cert) throws NotFoundException {
        Principal subjectDN = cert.getSubjectX500Principal();
        if (subjectDN != null) {
            return subjectDN.getName();
        } else {
            throw new NotFoundException("Subject DN not found in certificate.");
        }
    }

    public static String getSerialNumberFromX509Certificate(X509Certificate certificate) {
        return certificate.getSerialNumber().toString(16);
    }

    public static X509Certificate getX509Certificate(String certInBase64) throws CertificateException {
        return getX509Certificate(Base64.getDecoder().decode(certInBase64));
    }

    public static List<String> keyUsageExtractor(boolean[] keyUsage) {

        List<String> keyUsg = new ArrayList<>();
        try {
            for (int i = 0; i < keyUsage.length; i++) {
                if (keyUsage[i] == Boolean.TRUE) {
                    keyUsg.add((String) KEY_USAGE_LIST.toArray()[i]);
                }
            }
        } catch (NullPointerException e) {
            logger.warn("No Key Usage found");
        }
        return keyUsg;
    }

    public static String getBasicConstraint(int bcValue) {
        if (bcValue >= 0) {
            return "Subject Type=CA";
        } else {
            return "Subject Type=End Entity";
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getSAN(X509Certificate certificate) {
        @SuppressWarnings("serial")
        Map<String, Object> sans = new HashMap<>();
        sans.put("otherName", new ArrayList<String>());
        sans.put("rfc822Name", new ArrayList<String>());
        sans.put("dNSName", new ArrayList<String>());
        sans.put("x400Address", new ArrayList<String>());
        sans.put("directoryName", new ArrayList<String>());
        sans.put("ediPartyName", new ArrayList<String>());
        sans.put("uniformResourceIdentifier", new ArrayList<String>());
        sans.put("iPAddress", new ArrayList<String>());
        sans.put("registeredID", new ArrayList<String>());

        try {
            for (List<?> san : certificate.getSubjectAlternativeNames()) {
                ((ArrayList<String>) sans.get(SAN_TYPE_MAP.get(san.toArray()[0].toString())))
                        .add(san.toArray()[1].toString());
            }
        } catch (CertificateParsingException | NullPointerException e) {
            logger.warn("Unable to get the SAN of the certificate");
            logger.warn(e.getMessage());
        }

        return sans;
    }


    private static Map<String, Object> getSAN(JcaPKCS10CertificationRequest csr) {

        Map<String, Object> sans = new HashMap<>();
        sans.put("otherName", new ArrayList<>());
        sans.put("rfc822Name", new ArrayList<>());
        sans.put("dNSName", new ArrayList<>());
        sans.put("x400Address", new ArrayList<>());
        sans.put("directoryName", new ArrayList<>());
        sans.put("ediPartyName", new ArrayList<>());
        sans.put("uniformResourceIdentifier", new ArrayList<>());
        sans.put("iPAddress", new ArrayList<>());
        sans.put("registeredID", new ArrayList<>());

        Attribute[] certAttributes = csr.getAttributes();
        for (Attribute attribute : certAttributes) {
            if (attribute.getAttrType().equals(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest)) {
                Extensions extensions = Extensions.getInstance(attribute.getAttrValues().getObjectAt(0));
                GeneralNames gns = GeneralNames.fromExtensions(extensions, Extension.subjectAlternativeName);
                GeneralName[] names = gns.getNames();
                for (GeneralName name : names) {
                    switch (name.getTagNo()){
                        case GeneralName.dNSName: ((ArrayList<String>) sans.get("dNSName")).add(name.getName().toString());
                        case GeneralName.iPAddress: ((ArrayList<String>) sans.get("iPAddress")).add(name.getName().toString());
                        case GeneralName.otherName: ((ArrayList<String>) sans.get("otherName")).add(name.getName().toString());
                        case GeneralName.directoryName: ((ArrayList<String>) sans.get("directoryName")).add(name.getName().toString());
                        case GeneralName.registeredID: ((ArrayList<String>) sans.get("registeredID")).add(name.getName().toString());
                        case GeneralName.x400Address: ((ArrayList<String>) sans.get("x400Address")).add(name.getName().toString());
                        case GeneralName.uniformResourceIdentifier: ((ArrayList<String>) sans.get("uniformResourceIdentifier")).add(name.getName().toString());
                        case GeneralName.ediPartyName: ((ArrayList<String>) sans.get("ediPartyName")).add(name.getName().toString());
                        case GeneralName.rfc822Name: ((ArrayList<String>) sans.get("rfc822Name")).add(name.getName().toString());
                    }
                }
            }
        }
        return sans;
    }

    public static X509Certificate parseCertificate(String cert) throws CertificateException {
        cert = cert.replace("-----BEGIN CERTIFICATE-----", "").replace("-----END CERTIFICATE-----", "")
                .replace("\r", "").replace("\n", "");
        byte[] decoded = Base64.getDecoder().decode(cert);
        return getX509Certificate(decoded);
    }

    public static String getThumbprint(byte[] encodedContent)
            throws NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        messageDigest.update(encodedContent);
        byte[] digest = messageDigest.digest();
        String thumbprint = DatatypeConverter.printHexBinary(digest).toLowerCase();
        return thumbprint;
    }

    public static String getThumbprint(X509Certificate certificate)
            throws NoSuchAlgorithmException, CertificateEncodingException {
        return getThumbprint(certificate.getEncoded());
    }

    public static String getThumbprint(String certificate)
            throws NoSuchAlgorithmException, CertificateException {
        X509Certificate x509Certificate = getX509Certificate(normalizeCertificateContent(certificate));
        return getThumbprint(x509Certificate.getEncoded());
    }

    public static String normalizeCertificateContent(String content) {
        return content
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace("\r", "")
                .replace("\n", "");
    }

    public static Certificate prepareCertificate(Certificate modal, X509Certificate certificate) {
        modal.setSerialNumber(certificate.getSerialNumber().toString(16));
        setSubjectDNParams(modal, certificate.getSubjectX500Principal().toString());
        setIssuerDNParams(modal, certificate.getIssuerX500Principal().toString());
        modal.setNotAfter(certificate.getNotAfter());
        modal.setNotBefore(certificate.getNotBefore());
        if (certificate.getPublicKey() == null) {
            throw new ValidationException(
                    ValidationError.create(
                            "Invalid Certificate. Public Key is missing"
                    )
            );
        }
        try {
            modal.setPublicKeyFingerprint(getThumbprint(Base64.getEncoder().encodeToString(certificate.getPublicKey().getEncoded()).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to calculate the thumbprint of the certificate");
        }

        modal.setPublicKeyAlgorithm(getAlgorithmFromProviderName(certificate.getPublicKey().getAlgorithm()));
        modal.setSignatureAlgorithm(certificate.getSigAlgName().replace("WITH", "with"));
        modal.setStatus(CertificateStatus.UNKNOWN);
        modal.setKeySize(KeySizeUtil.getKeyLength(certificate.getPublicKey()));
        modal.setCertificateType(CertificateType.fromCode(certificate.getType()));
        modal.setSubjectAlternativeNames(MetaDefinitions.serialize(CertificateUtil.getSAN(certificate)));
        try {
            modal.setExtendedKeyUsage(MetaDefinitions.serializeArrayString(certificate.getExtendedKeyUsage()));
        } catch (CertificateParsingException e) {
            logger.warn("Unable to get the extended key usage. Failed to parse certificate");
            logger.error(e.getMessage());
        }
        modal.setKeyUsage(
                MetaDefinitions.serializeArrayString(CertificateUtil.keyUsageExtractor(certificate.getKeyUsage())));
        modal.setBasicConstraints(CertificateUtil.getBasicConstraint(certificate.getBasicConstraints()));

        return modal;
    }


    public static Certificate prepareCsrObject(Certificate modal, JcaPKCS10CertificationRequest certificate) throws NoSuchAlgorithmException, InvalidKeyException {
        setSubjectDNParams(modal, certificate.getSubject().toString());
        if (certificate.getPublicKey() == null) {
            throw new ValidationException(
                    ValidationError.create(
                            "Invalid Certificate. Public Key is missing"
                    )
            );
        }
        try {
            modal.setPublicKeyFingerprint(getThumbprint(Base64.getEncoder().encodeToString(certificate.getPublicKey().getEncoded()).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to calculate the thumbprint of the certificate");
        }

        modal.setPublicKeyAlgorithm(getAlgorithmFromProviderName(certificate.getPublicKey().getAlgorithm()));
        DefaultAlgorithmNameFinder algFinder = new DefaultAlgorithmNameFinder();
        modal.setSignatureAlgorithm(algFinder.getAlgorithmName(certificate.getSignatureAlgorithm()).replace("WITH", "with"));
        modal.setStatus(CertificateStatus.NEW);
        modal.setKeySize(KeySizeUtil.getKeyLength(certificate.getPublicKey()));
        modal.setSubjectAlternativeNames(MetaDefinitions.serialize(getSAN(certificate)));
        return modal;
    }




    private static void setIssuerDNParams(Certificate modal, String issuerDN) {
        modal.setIssuerDn(issuerDN);
        LdapName ldapName = null;
        try {
            ldapName = new LdapName(issuerDN);
        } catch (InvalidNameException e) {
            return;
        }
        for (Rdn i : ldapName.getRdns()) {
            if (i.getType().equals("CN")) {
                modal.setIssuerCommonName(i.getValue().toString());
            }
        }
    }

    private static void setSubjectDNParams(Certificate modal, String subjectDN) {
        modal.setSubjectDn(subjectDN);
        LdapName ldapName = null;

        try {
            ldapName = new LdapName(subjectDN);
        } catch (InvalidNameException e) {
            return;
        }
        for (Rdn i : ldapName.getRdns()) {
            if (i.getType().equals("CN")) {
                modal.setCommonName(i.getValue().toString());
            }
        }
    }

    public static String formatCsr(String unformattedCsr) {
        try {
            JcaPKCS10CertificationRequest jcaPKCS10CertificationRequest = csrStringToJcaObject(unformattedCsr);
            return JcaObjectToString(jcaPKCS10CertificationRequest);
        } catch (CertificateException e) {
            logger.debug("Failed to parse and format CSR : ", unformattedCsr);
            logger.error(e.getMessage());
            return unformattedCsr;
        }
    }

    private static JcaPKCS10CertificationRequest csrStringToJcaObject(String csr) throws CertificateException {
        csr = csr.replace("-----BEGIN CERTIFICATE REQUEST-----", "")
                .replaceAll(System.lineSeparator(), "")
                .replace("-----END CERTIFICATE REQUEST-----", "");
        byte[] decoded = Base64.getDecoder().decode(csr);
        try {
            return new JcaPKCS10CertificationRequest(decoded);
        } catch (IOException e) {
            throw new CertificateException("Failed to parse CSR. " + e.getMessage());
        }
    }

    private static String JcaObjectToString(JcaPKCS10CertificationRequest pkcs10CertificationRequest) throws CertificateException {
        try {
            PemObject pemCSR = new PemObject("CERTIFICATE REQUEST", pkcs10CertificationRequest.getEncoded());
            StringWriter decodedCsr = new StringWriter();
            JcaPEMWriter pemWriter = new JcaPEMWriter(decodedCsr);
            pemWriter.writeObject(pemCSR);
            pemWriter.close();
            decodedCsr.close();
            return decodedCsr.toString();
        } catch (IOException | NullPointerException e) {
            throw new CertificateException("Failed to format CSR. " + e.getMessage());
        }
    }

    public static String getAlgorithmFriendlyName(String algorithmName) {
        String friendlyName = CERTIFICATE_ALGORITHM_FRIENDLY_NAME.get(algorithmName);
        if (friendlyName != null) return friendlyName;
        return algorithmName;
    }

    public static String getAlgorithmFromProviderName(String providerName) {
        String name = CERTIFICATE_ALGORITHM_FROM_PROVIDER.get(providerName);
        if (name != null) return name;
        return providerName;
    }

}
