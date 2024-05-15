package com.czertainly.core.service.cmp.mock;

import com.czertainly.api.interfaces.core.cmp.error.CmpBaseException;
import com.czertainly.api.interfaces.core.cmp.error.CmpProcessingException;
import com.czertainly.core.service.cmp.configurations.ConfigurationContext;
import com.czertainly.core.service.cmp.message.builder.PkiMessageBuilder;
import com.czertainly.core.util.CertificateUtil;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cmp.*;
import org.bouncycastle.asn1.crmf.CertReqMessages;
import org.bouncycastle.asn1.crmf.CertTemplate;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.*;

public class MockCaImpl {

    private static final Logger LOG = LoggerFactory.getLogger(MockCaImpl.class.getName());

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

    public static LinkedList<X509Certificate> getChainOfIssuerCerts() { return chainOfIssuerCerts; }

    public static PrivateKey getPrivateKeyForSigning() { return SIGNING_CERT_PRIV_KEY; }

    /**
     * for ip, cp, kup
     *
     */
    public static PKIMessage handleCrmfCertificateRequest(PKIMessage request, ConfigurationContext config)
            throws CmpProcessingException {
        switch(request.getBody().getType()) {
            case PKIBody.TYPE_INIT_REQ:
            case PKIBody.TYPE_CERT_REQ:
            case PKIBody.TYPE_KEY_UPDATE_REQ:
                break;
            default:
                throw new IllegalStateException("cannot generate certResp for given message body/type, type="
                        +request.getBody().getType());
        }
        ASN1OctetString tid = request.getHeader().getTransactionID();
        X509Certificate newIssuedCert;
        List<CMPCertificate> extraCerts = null;
        try {

            CertTemplate requestTemplate = ((CertReqMessages)
                    request.getBody().getContent())
                    .toCertReqMsgArray()[0]
                    .getCertReq()
                    .getCertTemplate();
            SubjectPublicKeyInfo publicKey = requestTemplate.getPublicKey();
            X500Name subject = requestTemplate.getSubject();
            newIssuedCert = CertTestUtil.createCertificateV3(subject, publicKey,
                    chainOfIssuerCerts.get(0)/*issuingCert*/, requestTemplate.getExtensions());
            // remove ROOT CA certificate
            LinkedList<X509Certificate> withoutRootCa = new LinkedList<>(chainOfIssuerCerts);
            // withoutRootCa.remove(withoutRootCa.size() - 1);
            if(!withoutRootCa.isEmpty()) {
                extraCerts = new ArrayList<>(withoutRootCa.size());
                for (final X509Certificate x509Cert : withoutRootCa) {
                    extraCerts.add(CMPCertificate.getInstance(x509Cert.getEncoded()));
                }
            }
        } catch (Exception e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badCertTemplate, "problem with create certificate", e);
        }

        PKIMessage response;
        try {
            /*
             * See Section 5.3.4 for CertRepMessage syntax.  Note that if the PKI
             *    Message Protection is "shared secret information" (see Section
             *    5.1.3), then any certificate transported in the caPubs field may be
             *    directly trusted as a root CA certificate by the initiator.
             *    @see https://www.rfc-editor.org/rfc/rfc4210#section-5.3.2
             * Scope: ip, cp, kup, ccp
             * Location: (optional) CertRepMessage.caPubs
             */
            CMPCertificate[] caPubs = CertificateUtil.toCmpCertificates(chainOfIssuerCerts);/*new CMPCertificate[2];
            caPubs[1] = CMPCertificate.getInstance(CA_ROOT_CERT.getEncoded());
            caPubs[0] = CMPCertificate.getInstance(CA_INTERMEDIATE_CERT.getEncoded());*/


            response = new PkiMessageBuilder(config)
                    .addHeader(PkiMessageBuilder.buildBasicHeaderTemplate(request))
                    .addBody(PkiMessageBuilder.createIpCpKupBody(
                            request.getBody(),
                            CMPCertificate.getInstance(newIssuedCert.getEncoded()),
                            caPubs))
                    .addExtraCerts(extraCerts)
                    .build();
        } catch (Exception e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure, "problem with create response message", e);
        }

        return response;
    }

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
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.3.18">Certificate Confirmation Content</a>
     */
    public static PKIMessage handleCertConfirm(PKIMessage message, ConfigurationContext configuration)
            throws CmpProcessingException {
        try {
            return new PkiMessageBuilder(configuration)
                    .addHeader(PkiMessageBuilder.buildBasicHeaderTemplate(message))
                    .addBody(new PKIBody(PKIBody.TYPE_CONFIRM, new PKIConfirmContent()))
                    .addExtraCerts(getExtraCerts(chainOfIssuerCerts))
                    .build();
        } catch (Exception e) {
            throw new CmpProcessingException(message.getHeader().getTransactionID(), PKIFailureInfo.systemFailure,
                    "problem with create pkiConf response message", e);
        }
    }

    /**
     * <p>
     *    The revocation response is the response to the above message.  If
     *    produced, this is sent to the requester of the revocation.  (A
     *    separate revocation announcement message MAY be sent to the subject
     *    of the certificate for which revocation was requested.)</p>
     * <pre>
     *      RevRepContent ::= SEQUENCE {
     *          status        SEQUENCE SIZE (1..MAX) OF PKIStatusInfo,
     *          revCerts  [0] SEQUENCE SIZE (1..MAX) OF CertId OPTIONAL,
     *          crls      [1] SEQUENCE SIZE (1..MAX) OF CertificateList
     *                        OPTIONAL
     *      }
     * </pre>
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.3.9">Revocation requeset content</a>
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.3.10">Revocation response content</a>
     */
    public static PKIMessage handleRevocationRequest(PKIMessage message, ConfigurationContext configuration)
            throws CmpBaseException {
        RevReqContent revBody = (RevReqContent) message.getBody().getContent();
        RevDetails[] revDetails = revBody.toRevDetailsArray();

        RevRepContentBuilder rrcb = new RevRepContentBuilder();
        rrcb.add(new PKIStatusInfo(PKIStatus.granted));
        try {
            return new PkiMessageBuilder(configuration)
                    .addHeader(PkiMessageBuilder.buildBasicHeaderTemplate(message))
                    .addBody(new PKIBody(PKIBody.TYPE_REVOCATION_REP, rrcb.build()))
                    .addExtraCerts(getExtraCerts(chainOfIssuerCerts))
                    .build();
        } catch (Exception e) {
            throw new CmpProcessingException(message.getHeader().getTransactionID(), PKIFailureInfo.systemFailure,
                    " problem processing recovation response message", e);
        }
    }

    private static List<CMPCertificate> getExtraCerts(List<X509Certificate> chainOfIssuerCerts)
            throws CertificateEncodingException {
        List<CMPCertificate> extraCerts = null;
        if(!chainOfIssuerCerts.isEmpty()) {
            extraCerts = new ArrayList<>(chainOfIssuerCerts.size());
            for (final X509Certificate x509Cert : chainOfIssuerCerts) {
                extraCerts.add(CMPCertificate.getInstance(x509Cert.getEncoded()));
            }
        }
        return extraCerts;
    }
}
