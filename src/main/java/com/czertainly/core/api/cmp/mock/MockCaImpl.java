package com.czertainly.core.api.cmp.mock;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.message.ConfigurationContext;
import com.czertainly.core.api.cmp.message.builder.PkiMessageBuilder;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cmp.*;
import org.bouncycastle.asn1.crmf.CertReqMessages;
import org.bouncycastle.asn1.crmf.CertTemplate;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.X509KeyUsage;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class MockCaImpl {

    private static final Logger LOG = LoggerFactory.getLogger(MockCaImpl.class.getName());

    private static final JcaPEMKeyConverter JCA_KEY_CONVERTER = new JcaPEMKeyConverter();

    /*//
    private static KeyPair CA_ROOT_KEY_PAIR;
    private static X509Certificate CA_ROOT_CERT;
    private static KeyPair CA_INTERMEDIATE_KEY_PAIR;
    private static X509Certificate CA_INTERMEDIATE_CERT;
    //*/
    private static PrivateKey SIGNING_CERT_PRIV_KEY;
    private static LinkedList<X509Certificate> chainOfIssuerCerts;

    public static void init()
            throws Exception {
        /*
        // -- vytvareni keystore/certifikatu v pameti
        // -- (bohuzel, ale pak chybi cert pro openssl cmp klienta!)
        if(CA_ROOT_CERT == null) {
            // -- operator root CA
            CA_ROOT_KEY_PAIR = generateKeyPairEC();
            X500Name CA_ROOT_X500_NAME=new X500Name("CN=localhost, OU=root-ca-operator, O=*** Crypto., L=Pisek, ST=Czechia, C=CA");
            CA_ROOT_CERT = new CertificationGeneratorStrategy()
                    .generateCertificateCA(CA_ROOT_KEY_PAIR, CA_ROOT_KEY_PAIR,
                            CA_ROOT_X500_NAME, CA_ROOT_X500_NAME);
            // -- operator root CA
            CA_INTERMEDIATE_KEY_PAIR = generateKeyPairEC();
            X500Name CA_INTERMEDIATE_X500_NAME =new X500Name("CN=localhost, OU=intermediate-ca-operator, O=*** Crypto., L=Pisek, ST=Czechia, C=CA");
            CA_INTERMEDIATE_CERT = new CertificationGeneratorStrategy()
                    .generateCertificateCA(CA_ROOT_KEY_PAIR, CA_INTERMEDIATE_KEY_PAIR,
                            CA_ROOT_X500_NAME, CA_INTERMEDIATE_X500_NAME);
        }
        chainOfIssuerCerts = new LinkedList(List.of(
                CA_ROOT_CERT,
                CA_INTERMEDIATE_CERT));
        //*/

        KeyStore ks = KeystoreService.loadKeystoreFromFile("tc.p12", "tc".toCharArray());
//        KeystoreService.saveAsymmetricKey(
//                /* keystore     */ks,
//                /* alias        */"cmp-server",
//                /* caPrivateKey */CA_INTERMEDIATE_KEY_PAIR.getPrivate(), "tc",
//                CA_ROOT_CERT, CA_INTERMEDIATE_CERT);
        Map<PrivateKey, LinkedList<X509Certificate>> map = KeystoreService.loadKeyAndCertChain(ks, "tc".toCharArray());
        chainOfIssuerCerts = map.values().iterator().next();
        SIGNING_CERT_PRIV_KEY=map.keySet().iterator().next();
    }

    public static LinkedList<X509Certificate> getChainOfIssuerCerts() {
        return chainOfIssuerCerts;
    }

    /**
     * for ip, cp, kup
     *
     * @param request
     * @return
     * @throws Exception
     */
    public static PKIMessage handleCrmfCerticateRequest(PKIMessage request, ConfigurationContext config)
            throws CmpException {
        switch(request.getBody().getType()) {
            case PKIBody.TYPE_INIT_REQ:
            case PKIBody.TYPE_CERT_REQ:
            case PKIBody.TYPE_KEY_UPDATE_REQ:
                break;
            default:
                throw new IllegalStateException("cannot generate certResp for given message body/type, type="
                        +request.getBody().getType());
        }
        X509Certificate newIssuedCert = null;
        List<CMPCertificate> extraCerts = null;
        try {

            CertTemplate requestTemplate = ((CertReqMessages)
                    request.getBody().getContent())
                    .toCertReqMsgArray()[0]
                    .getCertReq()
                    .getCertTemplate();
            SubjectPublicKeyInfo publicKey = requestTemplate.getPublicKey();
            X500Name subject = requestTemplate.getSubject();
            newIssuedCert = createCertificateV2(subject, publicKey,
                    chainOfIssuerCerts.get(0)/*issuingCert*/, requestTemplate.getExtensions());
            // remove ROOT CA certificate - TODO [tocecz] jeste overit v 3gpp
            LinkedList<X509Certificate> withoutRootCa = new LinkedList<>(chainOfIssuerCerts);
            // withoutRootCa.remove(withoutRootCa.size() - 1);
            if(!withoutRootCa.isEmpty()) {
                extraCerts = new ArrayList<>(withoutRootCa.size());
                for (final X509Certificate x509Cert : withoutRootCa) {
                    extraCerts.add(CMPCertificate.getInstance(x509Cert.getEncoded()));
                }
            }
        } catch (Exception e) {
            throw new CmpException(PKIFailureInfo.badCertTemplate, "problem with create certificate", e);
        }

        PKIMessage response = null;

        try {
            /*
             * See Section 5.3.4 for CertRepMessage syntax.  Note that if the PKI
             *    Message Protection is "shared secret information" (see Section
             *    5.1.3), then any certificate transported in the caPubs field may be
             *    directly trusted as a root CA certificate by the initiator.
             *    @see https://www.rfc-editor.org/rfc/rfc4210#section-5.3.2
             * Scope: ip, cp, kup, ccp
             * Location: (optional) CertRepMessage.caPubs
             * RESULT: TODO [tomascejka] ke zvazeni, kde caPubs plnit a kdy?
             */
            CMPCertificate[] caPubs = null;/*new CMPCertificate[2];
            caPubs[1] = CMPCertificate.getInstance(CA_ROOT_CERT.getEncoded());
            caPubs[0] = CMPCertificate.getInstance(CA_INTERMEDIATE_CERT.getEncoded());*/


            response = new PkiMessageBuilder(config)
                    .addHeader(PkiMessageBuilder.buildBasicHeaderTemplate(request)/*new HeaderTemplate() {
                        @Override
                        public int getPvno() { return request.getHeader().getPvno().intValueExact(); }

                        @Override
                        public GeneralName getSender() { return null; }

                        @Override
                        public ASN1OctetString getSenderNonce() { return new DEROctetString(CertUtils.generateRandomBytes(16)); }

                        @Override
                        public GeneralName getRecipient() { return request.getHeader().getSender(); }


                        @Override
                        public ASN1OctetString getRecipNonce() { return request.getHeader().getSenderNonce(); }

                        @Override
                        public ASN1OctetString getTransactionID() { return request.getHeader().getTransactionID(); }

                        @Override
                        public InfoTypeAndValue[] getGeneralInfo() { return request.getHeader().getGeneralInfo(); }
                    }*/)
                    .addBody(PkiMessageBuilder.createIpCpKupBody(
                            request.getBody(),
                            CMPCertificate.getInstance(newIssuedCert.getEncoded()),
                            caPubs))
                    /*
                     * The extraCerts field can contain certificates that may be useful to
                     *      the recipient.  For example, this can be used by a CA or RA to
                     *      present an end entity with certificates that it needs to verify its
                     *      own new certificate (if, for example, the CA that issued the end
                     *      entity's certificate is not a root CA for the end entity).  Note that
                     *      this field does not necessarily contain a certification path; the
                     *      recipient may have to sort, select from, or otherwise process the
                     *      extra certificates in order to use them.
                     * Location:
                     *      (optional) PKIMessage.caPubs (na urovni header/body)
                     * @see https://www.rfc-editor.org/rfc/rfc4210#section-5.1
                     */
                    .addExtraCerts(extraCerts)
                    .build();
        } catch (Exception e) {
            throw new CmpException(PKIFailureInfo.systemFailure, "problem with create response message", e);
        }

        return response;
    }

    private static String getSigningAlgNameFromKeyAlg(final String keyAlgorithm) {
        if (keyAlgorithm.startsWith("Ed")) {// EdDSA key
            return keyAlgorithm;
        }
        if ("EC".equals(keyAlgorithm)) {// EC key
            return "SHA256withECDSA";
        }
        return "SHA256with" + keyAlgorithm;
    }

    // -- final
    private static X509Certificate createCertificateV2(
            X500Name subject,
            SubjectPublicKeyInfo publicKey,
            X509Certificate issuingCert,
            Extensions extensionsFromTemplate)
            throws NoSuchAlgorithmException, CertificateException, OperatorCreationException, CertIOException, PEMException {
        return new CertificationGeneratorStrategy().generateCertificate(
                subject, publicKey, issuingCert,
                SIGNING_CERT_PRIV_KEY, extensionsFromTemplate);
    }

    private static KeyPair generateKeyPairEC() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, NoSuchProviderException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME); // Initialize to generate asymmetric keys to be used with one of the Elliptic Curve algorithms
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp384r1"); // using domain parameters specified by safe curve spec of secp384r1
        keyPairGenerator.initialize(ecSpec, new SecureRandom("_n3coHodn@Kryptickeho!".getBytes()));
        return keyPairGenerator.generateKeyPair(); // Generate asymmetric keys.
    }

    public static PrivateKey getPrivateKeyForSigning() {
        return SIGNING_CERT_PRIV_KEY;
    }

    private static class CertificationGeneratorStrategy {
        public X509Certificate generateCertificateCA(KeyPair issuerKeyPair, KeyPair subjectKeyPair, X500Name issuer, X500Name subject)
                throws OperatorCreationException, CertificateException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, CertIOException, PEMException {
            SubjectPublicKeyInfo pubKeyInfo = SubjectPublicKeyInfo.getInstance(subjectKeyPair.getPublic().getEncoded());

            X509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                    /* issuer       */issuer,
                    /* serialNumber */new BigInteger(10, new SecureRandom()),
                    /* start        */new Date(),
                    /* until        */Date.from(LocalDate.now().plus(365*10, ChronoUnit.DAYS)
                                      .atStartOfDay().toInstant(ZoneOffset.UTC)),
                    /* subject      */subject,
                    /* publicKey    */pubKeyInfo
            );
            // Basic Constraints
            PublicKey pubKey = JCA_KEY_CONVERTER.getPublicKey(pubKeyInfo);
            final JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            //*
            certificateBuilder.addExtension(
                    new ASN1ObjectIdentifier("2.5.29.15"),
                    true,
                    new X509KeyUsage(
                            X509KeyUsage.digitalSignature |
                                    X509KeyUsage.nonRepudiation   |
                                    X509KeyUsage.keyEncipherment  |
                                    X509KeyUsage.cRLSign |
                                    X509KeyUsage.dataEncipherment));
            //*/
            certificateBuilder.addExtension(
                    Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(pubKey));
            certificateBuilder.addExtension(
                    Extension.basicConstraints, true, new BasicConstraints(true));//true is for CA
            // -------------------------------------

            // -- bouncy castle - certification singer
            PrivateKey issuerPrivateKey = issuerKeyPair.getPrivate();
            ContentSigner contentSigner = new JcaContentSignerBuilder(
                    getSigningAlgNameFromKeyAlg(issuerPrivateKey.getAlgorithm())) // /*"SHA256WithRSA"*/
                    .build(issuerPrivateKey);

            // -- create x.509 certificate
            X509Certificate certificate = new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME/*new BouncyCastleProvider()*/)
                    .getCertificate(certificateBuilder.build(contentSigner));
            return certificate;
        }

        /**
         * @see 3gpp mobile spec: 6.1.2	Interconnection CA Certificate profile
         */
        public X509Certificate generateCertificate(X500Name subject, SubjectPublicKeyInfo publicKey,
                                                   X509Certificate issuer, PrivateKey issuerPrivateKey,
                                                   Extensions extensionsFromTemplate)
                throws CertificateException, OperatorCreationException, CertIOException, PEMException, NoSuchAlgorithmException {
            PublicKey pubKey = JCA_KEY_CONVERTER.getPublicKey(publicKey);
            // -- bouncy castle - certification builder
            X509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                    /* issuer       */issuer.getSubjectX500Principal(),
                    /* serialNumber */new BigInteger(10, new SecureRandom()),
                    /* start        */new Date(),
                    /* until        */Date.from(LocalDate.now().plus(365, ChronoUnit.DAYS)
                            .atStartOfDay().toInstant(ZoneOffset.UTC)),
                    /* subject      */new X500Principal(subject.toString()),
                    /* publicKey    */pubKey
            );
            final JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            if (extensionsFromTemplate != null) {
                Arrays.stream(extensionsFromTemplate.getExtensionOIDs()).forEach(oid -> {
                    try {
                        certificateBuilder.addExtension(extensionsFromTemplate.getExtension(oid));
                    } catch (final CertIOException e) {
                        LOG.warn("Problem with add oid extension", e);
                    }
                });
            }
            //*
            certificateBuilder.addExtension(
                    new ASN1ObjectIdentifier("2.5.29.15"),
                    true,
                    new X509KeyUsage(
                            X509KeyUsage.digitalSignature |
                                    X509KeyUsage.nonRepudiation   |
                                    X509KeyUsage.keyEncipherment  |
                                    X509KeyUsage.cRLSign |
                                    X509KeyUsage.dataEncipherment));
            //*/
            certificateBuilder.addExtension(
                    Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(pubKey));
            certificateBuilder.addExtension(
                    Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(issuer));
            certificateBuilder.addExtension(
                    Extension.basicConstraints, true, new BasicConstraints(false));// <-- BasicConstraints: true for CA, false for EndEntity
            // -------------------------------------

            // -- bouncy castle - certification singer
            ContentSigner contentSigner = new JcaContentSignerBuilder(
                    getSigningAlgNameFromKeyAlg(issuerPrivateKey.getAlgorithm())) // /*"SHA256WithRSA"*/
                    .build(issuerPrivateKey);

            // -- create x.509 certificate
            X509Certificate certificate = new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME/*new BouncyCastleProvider()*/)
                    .getCertificate(certificateBuilder.build(contentSigner));

            return certificate;
        }

        public static Certificate selfSign(KeyPair keyPair, String subjectDN) throws OperatorCreationException, CertificateException, CertIOException {
            long now = System.currentTimeMillis();
            Date startDate = new Date(now);

            X500Name dnName = new X500Name(subjectDN);
            BigInteger certSerialNumber = new BigInteger(Long.toString(now)); // <-- Using the current timestamp as the certificate serial number

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(startDate);
            calendar.add(Calendar.YEAR, 1); // <-- 1 Yr validity

            Date endDate = calendar.getTime();
            String signatureAlgorithm = "SHA256WithRSA"; // <-- Use appropriate signature algorithm based on your keyPair algorithm.
            ContentSigner contentSigner = new JcaContentSignerBuilder(signatureAlgorithm)
                    .build(keyPair.getPrivate());
            JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    dnName, certSerialNumber, startDate, endDate, dnName, keyPair.getPublic());

            // Extensions --------------------------
            // Basic Constraints
            BasicConstraints basicConstraints = new BasicConstraints(true); // <-- true for CA, false for EndEntity
            certBuilder.addExtension(new ASN1ObjectIdentifier("2.5.29.19"), true, basicConstraints); // Basic Constraints is usually marked as critical.
            // -------------------------------------

            return new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(certBuilder.build(contentSigner));
        }
    }
    // -- final

    /**
     * <pre>
     *          CertConfirmContent ::= SEQUENCE OF CertStatus
     *
     *          CertStatus ::= SEQUENCE {
     *             certHash    OCTET STRING,
     *             certReqId   INTEGER,
     *             statusInfo  PKIStatusInfo OPTIONAL
     *          }
     * </pre>
     * @param message
     * @param configuration for given profile/request processing
     *
     * @return
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.3.18">Certificate Confirmation Content</a>
     */
    public PKIMessage handleCertConfirm(PKIMessage message, ConfigurationContext configuration) throws CmpException {
        try {
            return new PkiMessageBuilder(configuration)
                    .addHeader(PkiMessageBuilder.buildBasicHeaderTemplate(message))
                    .addBody(new PKIBody(PKIBody.TYPE_CONFIRM, new PKIConfirmContent()))
                    .build();
        } catch (Exception e) {
            throw new CmpException(PKIFailureInfo.systemFailure, "problem with create pkiConf response message", e);
        }
    }
}
