package com.czertainly.core.model.request;

import com.czertainly.api.exception.CertificateRequestException;
import com.czertainly.api.model.core.enums.CertificateRequestFormat;
import com.czertainly.core.util.CertificateUtil;
import lombok.Getter;
import org.bouncycastle.asn1.crmf.CertReqMessages;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.crmf.jcajce.JcaCertificateRequestMessage;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;

public class CrmfCertificateRequest implements CertificateRequest {

    private final byte[] encoded;
    @Getter
    private final JcaCertificateRequestMessage certificateRequestMessage;

    public CrmfCertificateRequest(byte[] request) throws CertificateRequestException {
        this.encoded = request;
        try {
            CertReqMessages certReqMessages = CertReqMessages.getInstance(request);
            // we take only the first request
            // TODO: support multiple requests
            this.certificateRequestMessage = new JcaCertificateRequestMessage(certReqMessages.toCertReqMsgArray()[0]);
        } catch (Exception e) {
            throw new CertificateRequestException("Cannot process CRMF request", e);
        }
    }

    @Override
    public byte[] getEncoded() {
        return encoded;
    }

    @Override
    public CertificateRequestFormat getFormat() {
        return CertificateRequestFormat.CRMF;
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
            throw new CertificateRequestException("Cannot get public key from certificate request.", e);
        }
    }

    @Override
    public Map<String, List<String>> getSubjectAlternativeNames() {
        return CertificateUtil.getSAN(this);
    }

    @Override
    public AlgorithmIdentifier getSignatureAlgorithm() {
        return certificateRequestMessage.getCertTemplate().getSigningAlg();
    }

}
