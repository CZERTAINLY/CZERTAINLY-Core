package com.czertainly.core.service.cmp.message.protection.impl;

import com.czertainly.core.api.cmp.error.CmpConfigurationException;
import com.czertainly.core.service.cmp.message.ConfigurationContext;
import com.czertainly.core.service.cmp.message.protection.ProtectionStrategy;
import com.czertainly.core.service.cmp.util.CertUtil;
import com.czertainly.core.service.cmp.util.OIDS;
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
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.OutputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.LinkedList;
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

    protected final LinkedList<X509Certificate> certificationsChain;

    public SingatureBaseProtectionStrategy(ConfigurationContext configuration){
        super(configuration);
        this.certificationsChain = new LinkedList<>(configuration.getExtraCertsCertificateChain());
    }

    @Override
    public AlgorithmIdentifier getProtectionAlg() throws CmpConfigurationException {
        return configuration.getSignatureAlgorithm().asANS1AlgorithmIdentifier();
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
        //Signature sig = Signature.getInstance(getProtectionAlg().getAlgorithm().getId());
        //sig.initSign(configuration.getPrivateKeyForSigning());
        //sig.update(new ProtectedPart(header, body).getEncoded(ASN1Encoding.DER));
        //return new DERBitString(sig.sign());

        ASN1EncodableVector v = new ASN1EncodableVector();
        v.addAll(new ASN1Encodable[]{header,body});
        //OIDS a1 = OIDS.findMatch(getProtectionAlg().getAlgorithm().getId());
        ContentSigner signer = new JcaContentSignerBuilder(
                configuration.getSignatureAlgorithm().asJcaName())
                .setProvider(configuration.getSignatureProvider())
                .build(configuration.getPrivateKeyForSigning());
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
}
