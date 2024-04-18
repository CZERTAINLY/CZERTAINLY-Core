package com.czertainly.core.api.cmp.message.protection;

import com.czertainly.core.api.cmp.message.ConfigurationContext;
import com.czertainly.core.api.cmp.message.util.CertUtils;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.cmp.CMPCertificate;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.cmp.PKIHeader;
import org.bouncycastle.asn1.cmp.ProtectedPart;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;

import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SingatureBaseProtectionStrategy extends BaseProtectionStrategy implements ProtectionStrategy {

    public SingatureBaseProtectionStrategy(ConfigurationContext configuration){
        super(configuration);
    }

    private String getSignatureAlgorithmName() {
        return this.configuration.getSignatureAlgorithmName();
    }

    @Override
    public AlgorithmIdentifier getProtectionAlg() {
        return new DefaultSignatureAlgorithmIdentifierFinder().find(getSignatureAlgorithmName());
    }

    @Override
    public DERBitString createProtection(PKIHeader header, PKIBody body) throws Exception {
        Signature sig = Signature.getInstance(getSignatureAlgorithmName());
        sig.initSign(configuration.getPrivateKeyForSigning());
        sig.update(new ProtectedPart(header, body).getEncoded(ASN1Encoding.DER));
        return new DERBitString(sig.sign());
    }

    @Override
    public List<CMPCertificate> getProtectingExtraCerts() throws CertificateException {
        final List<X509Certificate> certChain = getCertChain();
        if (certChain.size() <= 1) {
            // protecting cert might be selfsigned
            Arrays.asList(CertUtils.toCmpCertificates(certChain));
        }
        // filter out selfsigned certificates
        return certChain.stream()
                .filter(CertUtils::isIntermediateCertificate)
                .map(t -> {
                    try { return CertUtils.toCmpCertificate(t); }
                    catch (final CertificateException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * @return CA name
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.3.3">Sender in singnature-based protection at rfc4210</a>
     */
    @Override
    public GeneralName getSender() {
        return new GeneralName(X500Name.getInstance(getEndCertificate().getSubjectX500Principal().getEncoded()));
    }
}
