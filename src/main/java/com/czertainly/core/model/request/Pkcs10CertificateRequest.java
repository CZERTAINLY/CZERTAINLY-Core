package com.czertainly.core.model.request;

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

    public Pkcs10CertificateRequest(byte[] request) throws IOException {
        this.encoded = request;
        this.jcaObject =  new JcaPKCS10CertificationRequest(encoded);
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
    public PublicKey getPublicKey() throws NoSuchAlgorithmException, InvalidKeyException {
        return jcaObject.getPublicKey();
    }

    @Override
    public Map<String, Object> getSubjectAlternativeNames() {
        return CertificateUtil.getSAN(jcaObject, null);
    }

    @Override
    public AlgorithmIdentifier getSignatureAlgorithm() {
        return jcaObject.getSignatureAlgorithm();
    }


}
