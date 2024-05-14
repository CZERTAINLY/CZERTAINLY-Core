package com.czertainly.core.service.cmp.message.protection.impl;

import com.czertainly.api.model.core.cmp.ProtectionMethod;
import com.czertainly.api.interfaces.core.cmp.error.CmpConfigurationException;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.provider.key.CzertainlyPrivateKey;
import com.czertainly.core.service.cmp.message.CertificateKeyService;
import com.czertainly.core.service.cmp.configurations.ConfigurationContext;
import com.czertainly.core.service.cmp.message.PkiMessageDumper;
import com.czertainly.core.service.cmp.message.protection.ProtectionStrategy;
import com.czertainly.core.util.AlgorithmUtil;
import com.czertainly.core.util.CertificateUtil;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.cmp.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Extension;
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

import static com.czertainly.core.service.cmp.message.PkiMessageDumper.ifNotNull;

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
    private final CzertainlyPrivateKey privateKey;

    public SingatureBaseProtectionStrategy(ConfigurationContext configuration,
                                           AlgorithmIdentifier headerProtectionAlgorithm,
                                           CertificateKeyService certificateKeyServiceImpl)
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
        this.certificateKeyService = certificateKeyServiceImpl;
        this.privateKey = certificateKeyServiceImpl.getPrivateKey(configuration.getProfile().getSigningCertificate());
    }

    /**
     * <b>scope: SIGNATURE-BASED protection</b>
     *
     * @return get name of signature algorithm, which is configured at czertainly server
     *
     * @see <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html">Java Security Standard Algorithm Names Specification</a>
     */
    @Override
    public AlgorithmIdentifier getProtectionAlg() throws CmpConfigurationException {
        AlgorithmIdentifier signatureAlg = headerProtectionAlgorithm;
        if(ProtectionMethod.SHARED_SECRET.equals(configuration.getProtectionMethod())
                || signatureAlg == null) {
            signatureAlg = SIGNATURE_ALGORITHM_IDENTIFIER_FINDER.find("SHA256withECDSA");//fallback
            if(signatureAlg == null) {
                throw new CmpConfigurationException(PKIFailureInfo.systemFailure,
                        "wrong name of SIGNATURE algorithm");
            }
        }
        return signatureAlg;
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
        if (configuration.dumpSinging()) {
            PkiMessageDumper.dumpSingerCertificate(
                    "protection",
                    CertificateUtil.parseCertificate(configuration.getProfile().getSigningCertificate().getCertificateContent().getContent()),
                    null);
        }
        ContentSigner signer = new JcaContentSignerBuilder(
                AlgorithmUtil.getSignatureAlgorithmName(
                        getProtectionAlg().getAlgorithm().getId(),
                        privateKey.getAlgorithm()))
                .setProvider(certificateKeyService.getProvider(configuration.getProfile().getName()))
                .build(privateKey);
        OutputStream sOut = signer.getOutputStream();
        sOut.write(new org.bouncycastle.asn1.DERSequence(v).getEncoded(ASN1Encoding.DER));
        sOut.close();
        return new DERBitString(signer.getSignature());
    }

    @Override
    public List<CMPCertificate> getProtectingExtraCerts() throws CertificateException {
        final List<X509Certificate> certChain = certificationsChain;
        if (certChain.size() <= 1) {
            return Arrays.asList(CertificateUtil.toCmpCertificates(certChain));//self-signed CA structure
        }
        // if exist self-signed, remove them
        return certChain.stream()
                .filter(CertificateUtil::isIntermediateCertificate)//filter self-signed
                .map(t -> {
                    try { return CertificateUtil.toCmpCertificate(t); }
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
    public ASN1OctetString getSenderKID() {
        byte[] caExtensionValue = getCaCertificate().getExtensionValue(Extension.subjectKeyIdentifier.getId());
        return ifNotNull(
                caExtensionValue,
                x -> new org.bouncycastle.asn1.DEROctetString(
                        ASN1OctetString.getInstance(ASN1OctetString.getInstance(x).getOctets())
                                .getOctets()));
    }

    private X509Certificate getCaCertificate() { return certificationsChain.get(0); }

}
