package com.czertainly.core.model.request;

import com.czertainly.api.exception.CertificateRequestException;
import com.czertainly.core.util.CertificateUtil;
import org.bouncycastle.asn1.crmf.CRMFObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.crmf.CertificateRequestMessage;
import org.bouncycastle.cert.crmf.jcajce.JcaCertificateRequestMessage;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Map;

public class CrmfCertificateRequest implements CertificateRequest {

    private final byte[] encoded;
    private final JcaCertificateRequestMessage certificateRequestMessage;

    public CrmfCertificateRequest(byte[] request) {
        this.encoded = request;
        this.certificateRequestMessage = new JcaCertificateRequestMessage(request);
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
    public PublicKey getPublicKey() throws NoSuchAlgorithmException, CertificateRequestException {
        SubjectPublicKeyInfo subjectPublicKeyInfo = certificateRequestMessage.getCertTemplate().getPublicKey();
        try {
            return new JcaPEMKeyConverter().getPublicKey(subjectPublicKeyInfo);
        } catch (PEMException e) {
            throw new CertificateRequestException("Cannot get public key from certificate request.");
        }
    }

    @Override
    public Map<String, Object> getSubjectAlternativeNames() {
        return CertificateUtil.getSAN(this);
    }

    @Override
    public AlgorithmIdentifier getSignatureAlgorithm() {
        return certificateRequestMessage.getCertTemplate().getSigningAlg();
    }

    public JcaCertificateRequestMessage getCertificateRequestMessage() {
        return this.certificateRequestMessage;
    }

}
