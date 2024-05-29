package com.czertainly.core.util;

import com.czertainly.api.exception.CertificateRequestException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.certificate.X500RdnType;
import com.czertainly.api.model.core.compliance.ComplianceStatus;
import com.czertainly.api.model.core.cryptography.key.KeyState;
import com.czertainly.api.model.core.cryptography.key.KeyUsage;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CryptographicKeyItem;
import com.czertainly.core.model.request.CertificateRequest;
import com.czertainly.core.model.request.CrmfCertificateRequest;
import com.czertainly.core.model.request.Pkcs10CertificateRequest;
import jakarta.xml.bind.DatatypeConverter;
import org.bouncycastle.asn1.DLSequence;
import org.bouncycastle.asn1.DLTaggedObject;
import org.bouncycastle.asn1.pkcs.Attribute;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jcajce.provider.asymmetric.x509.CertificateFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultAlgorithmNameFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class CertificateUtil {

    private static final Map<String, String> CERTIFICATE_ALGORITHM_FROM_PROVIDER = new HashMap<>();
    private static final Map<String, String> CERTIFICATE_ALGORITHM_FRIENDLY_NAME = new HashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(CertificateUtil.class);
    private static final Map<Integer, String> SAN_TYPE_MAP = new HashMap<>();

    public static final String KEY_USAGE_KEY_CERT_SIGN = "keyCertSign";
    private static final List<String> KEY_USAGE_LIST = Arrays.asList("digitalSignature", "nonRepudiation", "keyEncipherment", "dataEncipherment", "keyAgreement", KEY_USAGE_KEY_CERT_SIGN, "cRLSign", "encipherOnly", "decipherOnly");

    static {
        SAN_TYPE_MAP.put(GeneralName.otherName, "otherName");
        SAN_TYPE_MAP.put(GeneralName.rfc822Name, "rfc822Name");
        SAN_TYPE_MAP.put(GeneralName.dNSName, "dNSName");
        SAN_TYPE_MAP.put(GeneralName.x400Address, "x400Address");
        SAN_TYPE_MAP.put(GeneralName.directoryName, "directoryName");
        SAN_TYPE_MAP.put(GeneralName.ediPartyName, "ediPartyName");
        SAN_TYPE_MAP.put(GeneralName.uniformResourceIdentifier, "uniformResourceIdentifier");
        SAN_TYPE_MAP.put(GeneralName.iPAddress, "iPAddress");
        SAN_TYPE_MAP.put(GeneralName.registeredID, "registeredID");
    }

    static {
        CERTIFICATE_ALGORITHM_FRIENDLY_NAME.put("SPHINCSPLUS", KeyAlgorithm.SPHINCSPLUS.getCode());
        CERTIFICATE_ALGORITHM_FRIENDLY_NAME.put("DILITHIUM", KeyAlgorithm.DILITHIUM.getCode());
    }

    static {
        CERTIFICATE_ALGORITHM_FROM_PROVIDER.put("EC", KeyAlgorithm.ECDSA.toString());
        CERTIFICATE_ALGORITHM_FROM_PROVIDER.put("SPHINCS+", KeyAlgorithm.SPHINCSPLUS.toString());
        CERTIFICATE_ALGORITHM_FROM_PROVIDER.put("Dilithium", KeyAlgorithm.DILITHIUM.toString());
        CERTIFICATE_ALGORITHM_FROM_PROVIDER.put("Falcon", KeyAlgorithm.FALCON.toString());
    }

    private CertificateUtil() {
    }

    public static X509Certificate getX509Certificate(byte[] decoded) throws CertificateException {
        try {
            X509Certificate certificate = (X509Certificate) new CertificateFactory().engineGenerateCertificate(new ByteArrayInputStream(decoded));
            if (certificate.getPublicKey() == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Certificate has no public key, trying to generate certificate with BouncyCastleProvider: {}", Base64.getEncoder().encodeToString(decoded));
                }
                java.security.cert.CertificateFactory certificateFactory = java.security.cert.CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME);
                return (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(decoded));
            }
            return certificate;
        } catch (Exception e) {
            throw new CertificateException("Failed to parse and create X509Certificate object from provided certificate content");
        }
    }

    public static X509Certificate getX509Certificate(String certInBase64) throws CertificateException {
        return getX509Certificate(Base64.getDecoder().decode(certInBase64));
    }

    public static X509Certificate getX509CertificateFromBase64Url(String certInBase64) throws CertificateException {
        return getX509Certificate(Base64.getUrlDecoder().decode(certInBase64));
    }

    public static String getBase64FromX509Certificate(X509Certificate certificate) throws CertificateException {
        return Base64.getEncoder().encodeToString(certificate.getEncoded());
    }

    public static List<String> keyUsageExtractor(boolean[] keyUsage) {
        List<String> keyUsageNames = new ArrayList<>();
        if (keyUsage == null) {
            return keyUsageNames;
        }

        for (int i = 0; i < keyUsage.length; i++) {
            if (Boolean.TRUE.equals(keyUsage[i])) {
                keyUsageNames.add(KEY_USAGE_LIST.get(i));
            }
        }
        return keyUsageNames;
    }

    public static boolean isKeyUsagePresent(boolean[] keyUsage, String keyUsageName) {
        if (keyUsage == null) {
            return false;
        }

        int keyUsageIndex = KEY_USAGE_LIST.indexOf(keyUsageName);
        return keyUsageIndex != -1 && keyUsage[keyUsageIndex];
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
        Map<String, Object> sans = buildEmptySans();

        Collection<List<?>> encodedSans = null;
        try {
            encodedSans = certificate.getSubjectAlternativeNames();
        } catch (CertificateParsingException e) {
            logger.warn("Unable to get the SANs of the certificate");
            logger.warn(e.getMessage());
        }

        if (encodedSans == null) {
            return sans;
        }

        for (List<?> san : encodedSans) {
            String sanTypeName = null;
            try {
                Integer sanType = (Integer) san.get(0);
                sanTypeName = SAN_TYPE_MAP.get(sanType);
                String value = getSanValueString(sanType, san.get(1));
                ((ArrayList<String>) sans.get(sanTypeName)).add(value);
            } catch (Exception e) {
                logger.warn("Unable to get the SAN {} of the certificate", sanTypeName);
                logger.warn(e.getMessage());
            }
        }

        return sans;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getSAN(CertificateRequest certificateRequest) {
        Map<String, Object> sans = buildEmptySans();

        GeneralNames gns = null;

        if (certificateRequest instanceof Pkcs10CertificateRequest) {
            Attribute[] certAttributes = ((Pkcs10CertificateRequest) certificateRequest).getJcaObject().getAttributes();
            for (Attribute attribute : certAttributes) {
                if (attribute.getAttrType().equals(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest)) {
                    Extensions extensions = Extensions.getInstance(attribute.getAttrValues().getObjectAt(0));
                    gns = GeneralNames.fromExtensions(extensions, Extension.subjectAlternativeName);
                    break;
                }
            }
        } else if (certificateRequest instanceof CrmfCertificateRequest) {
            Extensions extensions = Extensions.getInstance(((CrmfCertificateRequest) certificateRequest).getCertificateRequestMessage().getCertTemplate().getExtensions());
            gns = GeneralNames.fromExtensions(extensions, Extension.subjectAlternativeName);
        }

        if (gns == null) {
            return sans;
        }

        GeneralName[] names = gns.getNames();
        for (GeneralName name : names) {
            String sanTypeName = null;
            try {
                int sanType = name.getTagNo();
                sanTypeName = SAN_TYPE_MAP.get(sanType);
                String value = getSanValueString(sanType, sanType == GeneralName.otherName || sanType == GeneralName.ediPartyName ? name : name.getName());
                ((ArrayList<String>) sans.get(sanTypeName)).add(value);
            } catch (Exception e) {
                logger.warn("Unable to get the SAN {} of the certificate request", sanTypeName);
                logger.warn(e.getMessage());
            }
        }

        return sans;
    }

    private static String getSanValueString(Integer sanType, Object value) {
        if (sanType == GeneralName.otherName) {
            DLSequence otherNameSeq = (DLSequence) GeneralName.getInstance(value).getName();
            var oidSeq = otherNameSeq.getObjectAt(0).toString();
            var valueSeq = ((DLTaggedObject) otherNameSeq.getObjectAt(1)).getBaseObject().toString();
            return String.format("%s=%s", oidSeq, valueSeq);
        }
        if (sanType == GeneralName.ediPartyName) {
            DLSequence ediPartySeq = (DLSequence) GeneralName.getInstance(value).getName();
            if (ediPartySeq.size() < 1 || ediPartySeq.size() > 2) {
                return value.toString();
            }
            return ediPartySeq.size() == 1 ? "Party=" + ediPartySeq.getObjectAt(0).toString()
                    : String.format("Assigner=%s, Party=%s", ediPartySeq.getObjectAt(0).toString(), ediPartySeq.getObjectAt(1).toString());
        }

        return value.toString();
    }

    private static Map<String, Object> buildEmptySans() {
        return SAN_TYPE_MAP.values().stream().collect(Collectors.toMap(sanTypeName -> sanTypeName, sanTypeName -> new ArrayList<>(), (a, b) -> b));
    }

    public static X509Certificate parseUploadedCertificateContent(String certificateContent) {
        var decodedContent = Base64.getDecoder().decode(certificateContent);

        // check if certificate content is PEM encoded X.509 certificate
        boolean isPem = false;
        try (StringReader contentReader = new StringReader(new String(decodedContent));
             PEMParser pemParser = new PEMParser(contentReader)) {

            // alternative is to read straight with pemParser.readObject() and let to create object encoded in PEM by BouncyCastle
            // ideally creating own subclass of PEMParser to have the process under control
            PemObject pemObject = pemParser.readPemObject();

            // content contains PEM object
            if (pemObject != null) {
                logger.debug("PEM encoded certificate content uploaded. Type: {}", pemObject.getType());
                if (pemObject.getType().equals(PEMParser.TYPE_CERTIFICATE)
                        || pemObject.getType().equals(PEMParser.TYPE_X509_CERTIFICATE)
                        || pemObject.getType().equals(PEMParser.TYPE_PKCS7)) {
                    isPem = true;
                    decodedContent = pemObject.getContent();
                } else {
                    throw new ValidationException("Uploaded PEM encoded content is not certificate. Uploaded PEM type: " + pemObject.getType());
                }
            }
        } catch (IOException e) {
            logger.debug("Failed to parse uploaded certificate content as PEM encoded.");
        }

        // if not PEM is uploaded, check if it is not supported PKCS#12 format
        logger.debug("Binary certificate content uploaded");
        if (!isPem) {
            try (ByteArrayInputStream contentStream = new ByteArrayInputStream(decodedContent)) {
                KeyStore ks = KeyStore.getInstance("pkcs12", BouncyCastleProvider.PROVIDER_NAME);
                ks.load(contentStream, null);
            } catch (Exception e) {
                if (e.getMessage().equals("no password supplied when one expected")) {
                    throw new ValidationException("Unsupported certificate format PKCS#12");
                }
                logger.debug("Uploaded certificate is not PKCS12. Try parse content as binary X509Certificate");
            }
        }

        try {
            return getX509Certificate(decodedContent);
        } catch (CertificateException e) {
            logger.debug("Failed to parse content as binary X509Certificate: {}", e.getMessage());
            throw new ValidationException("Uploaded certificate content has invalid or unsupported format.");
        }
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
        return DatatypeConverter.printHexBinary(digest).toLowerCase();
    }

    public static String getSha1Thumbprint(byte[] encodedContent)
            throws NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
        messageDigest.update(encodedContent);
        byte[] digest = messageDigest.digest();
        return DatatypeConverter.printHexBinary(digest).toLowerCase();
    }

    public static String getThumbprint(X509Certificate certificate)
            throws NoSuchAlgorithmException, CertificateEncodingException {
        return getThumbprint(certificate.getEncoded());
    }

    public static String normalizeCertificateContent(String content) {
        return content
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace("\r", "")
                .replace("\n", "");
    }

    public static void prepareIssuedCertificate(Certificate modal, X509Certificate certificate) {
        modal.setState(CertificateState.ISSUED);
        modal.setComplianceStatus(ComplianceStatus.NOT_CHECKED);
        modal.setValidationStatus(CertificateValidationStatus.NOT_CHECKED);

        modal.setSerialNumber(certificate.getSerialNumber().toString(16));
        byte[] subjectDnPrincipalEncoded = certificate.getSubjectX500Principal().getEncoded();
        byte[] issuerDnPrincipalEncoded = certificate.getIssuerX500Principal().getEncoded();
        setSubjectDNParams(modal, X500Name.getInstance(CzertainlyX500NameStyle.DEFAULT, subjectDnPrincipalEncoded));
        setIssuerDNParams(modal, X500Name.getInstance(CzertainlyX500NameStyle.DEFAULT, issuerDnPrincipalEncoded));
        modal.setIssuerDnNormalized(X500Name.getInstance(CzertainlyX500NameStyle.NORMALIZED, issuerDnPrincipalEncoded).toString());
        modal.setSubjectDnNormalized(X500Name.getInstance(CzertainlyX500NameStyle.NORMALIZED, subjectDnPrincipalEncoded).toString());
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

        String basicConstraints = CertificateUtil.getBasicConstraint(certificate.getBasicConstraints());
        modal.setBasicConstraints(basicConstraints);
        // Set trusted certificate mark either for CA or for self-signed certificate
        if (basicConstraints.equals("Subject Type=CA") || Objects.equals(modal.getSubjectDnNormalized(), modal.getIssuerDnNormalized()))
            modal.setTrustedCa(false);
    }


    public static void prepareCsrObject(Certificate modal, CertificateRequest certificateRequest) throws NoSuchAlgorithmException, CertificateRequestException {
        setSubjectDNParams(modal, X500Name.getInstance(CzertainlyX500NameStyle.DEFAULT, certificateRequest.getSubject()));
        if (certificateRequest.getPublicKey() == null) {
            throw new ValidationException(
                    ValidationError.create(
                            "Invalid public key in certificate request"
                    )
            );
        }
        try {
            modal.setPublicKeyFingerprint(getThumbprint(Base64.getEncoder().encodeToString(certificateRequest.getPublicKey().getEncoded()).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to get the thumbprint of the certificate request: {}", e.getMessage());
        }

        modal.setPublicKeyAlgorithm(getAlgorithmFromProviderName(certificateRequest.getPublicKey().getAlgorithm()));
        DefaultAlgorithmNameFinder algFinder = new DefaultAlgorithmNameFinder();
        if (certificateRequest.getSignatureAlgorithm() == null)
            modal.setSignatureAlgorithm(null);
        else
            modal.setSignatureAlgorithm(algFinder.getAlgorithmName(certificateRequest.getSignatureAlgorithm()).replace("WITH", "with"));
        modal.setKeySize(KeySizeUtil.getKeyLength(certificateRequest.getPublicKey()));
        modal.setSubjectAlternativeNames(MetaDefinitions.serialize(certificateRequest.getSubjectAlternativeNames()));
    }

    private static void setIssuerDNParams(Certificate modal, X500Name issuerDN) {
        modal.setIssuerDn(issuerDN.toString());

        for (RDN i : issuerDN.getRDNs()) {
            if (i.getFirst() == null) continue;

            if (X500RdnType.COMMON_NAME.getOID().equals(i.getFirst().getType().getId())) {
                modal.setIssuerCommonName(i.getFirst().getValue().toString());
            }
        }
    }

    private static void setSubjectDNParams(Certificate modal, X500Name subjectDN) {
        modal.setSubjectDn(subjectDN.toString());

        for (RDN i : subjectDN.getRDNs()) {
            if (i.getFirst() == null) continue;

            if (X500RdnType.COMMON_NAME.getOID().equals(i.getFirst().getType().getId())) {
                modal.setCommonName(i.getFirst().getValue().toString());
            }
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

    public static List<JcaX509CertificateHolder> convertToX509CertificateHolder(List<X509Certificate> certificateChain) throws CertificateEncodingException {
        List<JcaX509CertificateHolder> certificateHolderChain = new ArrayList<>();
        for (X509Certificate certificate : certificateChain) {
            certificateHolderChain.add(new JcaX509CertificateHolder(certificate));
        }
        return certificateHolderChain;
    }

    public static boolean isCertificateScepCaCertAcceptable(Certificate certificate, boolean intuneEnabled) {
        if (certificate.getKey() == null || !certificate.getState().equals(CertificateState.ISSUED) || (!certificate.getValidationStatus().equals(CertificateValidationStatus.VALID) && !certificate.getValidationStatus().equals(CertificateValidationStatus.EXPIRING))) {
            return false;
        }

        // Check if the public key has usage ENCRYPT enabled and private key has DECRYPT and SIGN enabled
        // It is required to check RSA for public key since only RSA keys are encryption capable
        // Other types of keys such as split keys and secret keys are not needed to be checked since they cannot be used in certificates
        boolean privateKeyAvailable = false;
        for (CryptographicKeyItem item : certificate.getKey().getItems()) {
            if ((intuneEnabled && !item.getKeyAlgorithm().equals(KeyAlgorithm.RSA))
                    || (!intuneEnabled && !item.getKeyAlgorithm().equals(KeyAlgorithm.RSA) && !item.getKeyAlgorithm().equals(KeyAlgorithm.ECDSA))) {
                return false;
            } else if (item.getKeyAlgorithm().equals(KeyAlgorithm.RSA) && item.getType().equals(KeyType.PUBLIC_KEY)) {
                if (!item.getUsage().containsAll(List.of(KeyUsage.ENCRYPT, KeyUsage.VERIFY))) {
                    return false;
                }
            } else if (item.getKeyAlgorithm().equals(KeyAlgorithm.RSA) && item.getType().equals(KeyType.PRIVATE_KEY)) {
                if (item.getState() != KeyState.ACTIVE || !item.getUsage().containsAll(List.of(KeyUsage.DECRYPT, KeyUsage.SIGN))) {
                    return false;
                }
                privateKeyAvailable = true;
            } else if (item.getKeyAlgorithm().equals(KeyAlgorithm.ECDSA) && item.getType().equals(KeyType.PUBLIC_KEY)) {
                if (!item.getUsage().containsAll(List.of(KeyUsage.VERIFY))) {
                    return false;
                }
            } else if (item.getKeyAlgorithm().equals(KeyAlgorithm.ECDSA) && item.getType().equals(KeyType.PRIVATE_KEY)) {
                if (item.getState() != KeyState.ACTIVE || !item.getUsage().containsAll(List.of(KeyUsage.SIGN))) {
                    return false;
                }
                privateKeyAvailable = true;
            }
        }
        return privateKeyAvailable;
    }

    public static String generateRandomX509CertificateBase64(KeyPair keyPair) throws CertificateException, NoSuchAlgorithmException, SignatureException, InvalidKeyException, NoSuchProviderException, OperatorCreationException {
        return Base64.getEncoder().encodeToString(generateRandomX509Certificate(keyPair).getEncoded());
    }

    public static X509Certificate generateRandomX509Certificate(KeyPair keyPair) throws NoSuchAlgorithmException, CertificateException, SignatureException, InvalidKeyException, NoSuchProviderException, OperatorCreationException {
        SecureRandom random = new SecureRandom();

        X500Name owner = new X500Name("CN=generatedCertificate,O=random");

        // Current time minus 1 year, just in case software clock goes back due to time synchronization
        Date notBefore = new Date(System.currentTimeMillis() - 86400000L * 365);
        // Random date between the generated time and 1 year from now
        Date notAfter = between(new Date(System.currentTimeMillis() - 86400000L * 365),
                new Date(System.currentTimeMillis() + 86400000L * 365));

        if (keyPair == null) {
            keyPair = generateRandomKeyPair();
        }

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                owner, new BigInteger(64, random), notBefore, notAfter, owner, keyPair.getPublic());

        PrivateKey privateKey = keyPair.getPrivate();
        ContentSigner signer = new JcaContentSignerBuilder("SHA512WithRSAEncryption").build(privateKey);
        X509CertificateHolder certHolder = builder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider(new BouncyCastleProvider())
                .getCertificate(certHolder);

        //check so that cert is valid
        cert.verify(keyPair.getPublic());

        return cert;
    }

    public static Date between(Date startInclusive, Date endExclusive) {
        long startMillis = startInclusive.getTime();
        long endMillis = endExclusive.getTime();
        long randomMillisSinceEpoch = ThreadLocalRandom
                .current()
                .nextLong(startMillis, endMillis);

        return new Date(randomMillisSinceEpoch);
    }

    public static KeyPair generateRandomKeyPair() throws NoSuchAlgorithmException {
        SecureRandom random = new SecureRandom();

        KeyPair keyPair;
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");

        List<Integer> keySizeList = Arrays.asList(1024, 2048, 4096);
        int randomKeySize = keySizeList.get(random.nextInt(keySizeList.size()));

        keyPairGenerator.initialize(randomKeySize);
        keyPair = keyPairGenerator.generateKeyPair();

        return keyPair;
    }

    public static byte[] getContentFromLdap(String ldapUrl) throws Exception {
        Hashtable<String, String> environment = new Hashtable<String, String>();
        environment.put(Context.SECURITY_PRINCIPAL, "CN=Klara Ficova,CN=Users,DC=winlab,DC=3key,DC=company");
        environment.put(Context.SECURITY_CREDENTIALS, "PTei06tpfjGvqAX");
        SearchControls searchControls = new SearchControls();
        // Split LDAP url of format ldap://baseDn?attribute?scope?filter;format, format is ignored if included, because
        // return value is bytes always
        String[] splitUrl = ldapUrl.split("[?;]");
        String baseDn = splitUrl[0];
        if (splitUrl.length < 2) throw new Exception("Missing attribute in LDAP url.");
        String attribute = splitUrl[1];
        String scope = "base";
        if (splitUrl.length >= 3) scope = splitUrl[2];
        String filter = null;
        if (splitUrl.length >= 4) filter = splitUrl[3];

        switch (scope.toLowerCase()) {
            case "base" -> searchControls.setSearchScope(SearchControls.OBJECT_SCOPE);
            case "one" -> searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            case "sub" -> searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            default -> throw new IllegalArgumentException("Invalid search scope in LDAP url.");
        }
        DirContext ctx;
        try {
            ctx = new InitialDirContext(environment);

            NamingEnumeration<SearchResult> results = ctx.search(baseDn, filter, searchControls);
            if (results.hasMore()) {
                SearchResult result = results.next();
                Attributes attributes = result.getAttributes();
                return (byte[]) attributes.get(attribute).get();
            } else {
                return null;
            }
        } catch (NamingException e) {
            throw new Exception("Cannot retrieve content from LDAP, reason: " + e);
        }

    }

}
