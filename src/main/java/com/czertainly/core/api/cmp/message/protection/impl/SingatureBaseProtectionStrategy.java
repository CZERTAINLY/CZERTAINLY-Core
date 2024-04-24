package com.czertainly.core.api.cmp.message.protection.impl;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.message.ConfigurationContext;
import com.czertainly.core.api.cmp.message.protection.ProtectionStrategy;
import com.czertainly.core.api.cmp.util.CertUtil;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.cmp.CMPCertificate;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.cmp.PKIHeader;
import org.bouncycastle.asn1.cmp.ProtectedPart;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.GeneralName;

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

    @Override
    public AlgorithmIdentifier getProtectionAlg() throws CmpException {
        return this.configuration.getSignatureAlgorithm();
    }

    @Override
    public DERBitString createProtection(PKIHeader header, PKIBody body) throws Exception {
        Signature sig = Signature.getInstance(getProtectionAlg().getAlgorithm().getId());
        sig.initSign(configuration.getPrivateKeyForSigning());//podivat se do SCMP, podepisuje se via provider/CzertainlyProvider, + ScepResposne.createSignedData
        sig.update(new ProtectedPart(header, body).getEncoded(ASN1Encoding.DER));
        return new DERBitString(sig.sign());
    }

    @Override
    public List<CMPCertificate> getProtectingExtraCerts() throws CertificateException {
        final List<X509Certificate> certChain = getCertChain();
        if (certChain.size() <= 1) {
            return Arrays.asList(CertUtil.toCmpCertificates(certChain));//self-signed
        }
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
     * @return CA name
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.3.3">Sender in signature-based protection at rfc4210</a>
     */
    @Override
    public GeneralName getSender() {
        return new GeneralName(X500Name.getInstance(getEndCertificate().getSubjectX500Principal().getEncoded()));
    }

    @Override
    public DEROctetString getSenderKID() {
        return CertUtil.extractSubjectKeyIdentifierFromCert(getEndCertificate());
    }
}
