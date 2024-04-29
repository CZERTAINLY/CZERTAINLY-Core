package com.czertainly.core.model.request;

import com.czertainly.api.exception.CertificateRequestException;
import com.czertainly.core.util.CertificateUtil;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Map;

public class Pkcs10CertificateRequest implements CertificateRequest {

    private final byte[] encoded;
    private final JcaPKCS10CertificationRequest jcaObject;

    public Pkcs10CertificateRequest(byte[] request) throws CertificateRequestException {
        this.encoded = request;
        try {
            this.jcaObject =  new JcaPKCS10CertificationRequest(encoded);
        } catch (IOException e) {
            throw new CertificateRequestException("Error when parsing encoded PKCS10 file.");
        }
    }

    @Override
    public byte[] getEncoded() {
        return encoded;
    }

    @Override
    public X500Name getSubject() {
        return jcaObject.getSubject();
    }

    @Override
    public PublicKey getPublicKey() throws NoSuchAlgorithmException, CertificateRequestException {
        try {
            return jcaObject.getPublicKey();
        } catch (InvalidKeyException e) {
            throw new CertificateRequestException("Cannot get public key from certificate request.");
        }
    }

    @Override
    public Map<String, Object> getSubjectAlternativeNames() {
        return CertificateUtil.getSAN(this);
    }

    @Override
    public AlgorithmIdentifier getSignatureAlgorithm() {
        return jcaObject.getSignatureAlgorithm();
    }

    public JcaPKCS10CertificationRequest getJcaObject() { return jcaObject;}

}
