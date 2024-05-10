package com.czertainly.core.service.cmp.message.protection.impl;

import com.czertainly.api.model.core.cmp.ProtectionMethod;
import com.czertainly.core.api.cmp.error.CmpConfigurationException;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.service.cmp.CertificateKeyService;
import com.czertainly.core.service.cmp.message.ConfigurationContext;
import com.czertainly.core.service.cmp.message.PkiMessageDumper;
import com.czertainly.core.service.cmp.message.protection.ProtectionStrategy;
import com.czertainly.core.service.cmp.util.AlgorithmHelper;
import com.czertainly.core.service.cmp.util.CertUtil;
import com.czertainly.core.util.CertificateUtil;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.cmp.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p></p>Implementation of signature-based (see rfc4210, 5.1.3) protection of {@link PKIMessage}.
 * When protection is applied, the following structure is used:</p>
 *
 * <pre>
 *         PKIProtection ::= BIT STRING
 *
 *         ProtectedPart ::= SEQUENCE {
 *             header    PKIHeader,
 *             body      PKIBody
 *         }
 * </pre>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.3">PKI Message Protection</a>
 */
public class SingatureBaseProtectionStrategy extends BaseProtectionStrategy implements ProtectionStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(SingatureBaseProtectionStrategy.class.getName());

    private final DefaultSignatureAlgorithmIdentifierFinder SIGNATURE_ALGORITHM_IDENTIFIER_FINDER =
            new DefaultSignatureAlgorithmIdentifierFinder();

    private final List<X509Certificate> certificationsChain;
    private final CertificateKeyService certificateKeyService;

    public SingatureBaseProtectionStrategy(ConfigurationContext configuration,
                                           AlgorithmIdentifier headerProtectionAlgorithm,
                                           CertificateKeyService certificateKeyService)
            throws CmpConfigurationException {
        super(configuration, headerProtectionAlgorithm);
        try {
            Certificate signingCertificate = configuration.getProfile().getSigningCertificate();
            this.certificationsChain = List.of(CertificateUtil.getX509Certificate(
                    signingCertificate.getCertificateContent().getContent()));
        } catch (CertificateException e) {
            throw new CmpConfigurationException(PKIFailureInfo.systemFailure,
                    "problem to get singerCertificate");
        }
        this.certificateKeyService = certificateKeyService;
    }

    @Override
    public AlgorithmIdentifier getProtectionAlg() throws CmpConfigurationException {
        return getSignatureAlgorithm().asANS1AlgorithmIdentifier();
    }

    /**
     * Create protection {@link PKIMessage#getProtection()} field from <code>header</code> and <code>body</code>
     * (see rfc4210, section 5.1.3). Using algorithm defined at MSG_SIG_ALG (see rfc4210, D.2).
     *
     * @param header part {@link PKIHeader} for protection
     * @param body part  {@link PKIBody} for protection
     *
     * @return {@link PKIMessage#getProtection()}
     * @throws Exception if anything (create protection, but build signature object also) failed
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.3">PKI Message Protection</a>
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.3.3">Signature-based protection</a>
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#appendix-D.2">Algorithm Use Profile</a>
     */
    @Override
    public DERBitString createProtection(PKIHeader header, PKIBody body) throws Exception {
        ASN1EncodableVector v = new ASN1EncodableVector();
        v.addAll(new ASN1Encodable[]{header,body});
        //OIDS a1 = OIDS.findMatch(getProtectionAlg().getAlgorithm().getId());
        if(configuration.dumpSinging()) {
            PkiMessageDumper.dumpSingerCertificate(
                    "protection",
                    CertificateUtil.parseCertificate(configuration.getProfile().getSigningCertificate().getCertificateContent().getContent()),
                    null);
        }
        ContentSigner signer = new JcaContentSignerBuilder(
                getSignatureAlgorithm().asJcaName())
                .setProvider(certificateKeyService.getProvider(configuration.getProfile().getName()))
                .build(certificateKeyService.getPrivateKey(configuration.getProfile().getSigningCertificate()));
        OutputStream sOut = signer.getOutputStream();
        sOut.write(new org.bouncycastle.asn1.DERSequence(v).getEncoded(ASN1Encoding.DER));
        sOut.close();
        return new DERBitString(signer.getSignature());
    }

    @Override
    public List<CMPCertificate> getProtectingExtraCerts() throws CertificateException {
        final List<X509Certificate> certChain = certificationsChain;
        if (certChain.size() <= 1) {
            return Arrays.asList(CertUtil.toCmpCertificates(certChain));//self-signed CA structure
        }
        // if exist self-signed, remove them
        return certChain.stream()
                .filter(CertUtil::isIntermediateCertificate)//filter self-signed
                .map(t -> {
                    try { return CertUtil.toCmpCertificate(t); }
                    catch (final CertificateException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * @return CA name from Subject/Principal from CA cert
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.3.3">Sender in signature-based protection at rfc4210</a>
     */
    @Override
    public GeneralName getSender() {
        return new GeneralName(X500Name.getInstance(getCaCertificate().getSubjectX500Principal().getEncoded()));
    }

    /**
     * @return extract subject key identifier from CA cert
     */
    @Override
    public ASN1OctetString getSenderKID() { return CertUtil.extractSubjectKeyIdentifierFromCert(getCaCertificate()); }

    private X509Certificate getCaCertificate() { return certificationsChain.get(0); }

    /**
     * <b>scope: SIGNATURE-BASED protection</b>
     *
     * @return get name of signature algorithm, which is configured at czertainly server
     *
     * @see <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html">Java Security Standard Algorithm Names Specification</a>
     */
    private AlgorithmHelper getSignatureAlgorithm() throws CmpConfigurationException {
        AlgorithmIdentifier signatureAlg = headerProtectionAlgorithm;
        if(ProtectionMethod.SHARED_SECRET.equals(configuration.getProtectionMethod())
                || signatureAlg == null) {
            signatureAlg = SIGNATURE_ALGORITHM_IDENTIFIER_FINDER.find("SHA256withECDSA");//db query/cmp profile.getSignatureName
            if(signatureAlg == null) {
                throw new CmpConfigurationException(PKIFailureInfo.systemFailure,
                        "wrong name of SIGNATURE algorithm");
            }
        }
        return new AlgorithmHelper(signatureAlg);
    }
}
