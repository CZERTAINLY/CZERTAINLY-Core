package com.czertainly.core.model.request;

import com.czertainly.core.util.CertificateUtil;
import org.bouncycastle.asn1.crmf.CRMFObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.crmf.CertificateRequestMessage;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Map;

public class CrmfCertificateRequest implements CertificateRequest {

    private final byte[] encoded;
    private final CertificateRequestMessage certificateRequestMessage;

    public CrmfCertificateRequest(byte[] request) throws IOException {
        this.encoded = request;
        this.certificateRequestMessage = new CertificateRequestMessage(request);
    }

    @Override
    public byte[] getEncoded() {
        return encoded;
    }

    @Override
    public X500Name getSubject() {
        return certificateRequestMessage.getCertTemplate().getSubject();
    }

    @Override
    public PublicKey getPublicKey() throws NoSuchAlgorithmException, InvalidKeyException, PEMException {
        SubjectPublicKeyInfo subjectPublicKeyInfo = certificateRequestMessage.getCertTemplate().getPublicKey();
        return new JcaPEMKeyConverter().getPublicKey(subjectPublicKeyInfo);
    }

    @Override
    public Map<String, Object> getSubjectAlternativeNames() {
        return CertificateUtil.getSAN(null, certificateRequestMessage.getCertTemplate());
    }

    @Override
    public AlgorithmIdentifier getSignatureAlgorithm() {
        return certificateRequestMessage.getCertTemplate().getSigningAlg();
    }

}
