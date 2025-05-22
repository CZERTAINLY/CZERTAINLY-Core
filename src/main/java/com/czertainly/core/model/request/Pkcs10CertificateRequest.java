package com.czertainly.core.model.request;

import com.czertainly.api.exception.CertificateRequestException;
import com.czertainly.api.model.core.enums.CertificateRequestFormat;
import com.czertainly.core.util.CertificateUtil;
import lombok.Getter;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.SubjectAltPublicKeyInfo;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;
import java.util.List;

public class Pkcs10CertificateRequest implements CertificateRequest {

    private final byte[] encoded;
    @Getter
    private final JcaPKCS10CertificationRequest jcaObject;

    public Pkcs10CertificateRequest(byte[] request) throws CertificateRequestException {
        this.encoded = request;
        try {
            this.jcaObject =  new JcaPKCS10CertificationRequest(encoded);
        } catch (Exception e) {
            throw new CertificateRequestException("Cannot process PKCS#10 request", e);
        }
    }

    @Override
    public byte[] getEncoded() {
        return encoded;
    }

    @Override
    public CertificateRequestFormat getFormat() {
        return CertificateRequestFormat.PKCS10;
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
            throw new CertificateRequestException("Cannot get public key from certificate request.", e);
        }
    }

    @Override
    public PublicKey getAltPublicKey() throws NoSuchAlgorithmException, CertificateRequestException {
        Extensions requestedExtensions = jcaObject.getRequestedExtensions();
        if (requestedExtensions == null) return null;
        Extension extension = requestedExtensions.getExtension(Extension.subjectAltPublicKeyInfo);
        if (extension == null) return null;
        SubjectAltPublicKeyInfo altPublicKeyInfo = SubjectAltPublicKeyInfo.getInstance(extension.getParsedValue());
        KeyFactory keyFactory = KeyFactory.getInstance(altPublicKeyInfo.getAlgorithm().getAlgorithm().getId());
        try {
            return keyFactory.generatePublic(new X509EncodedKeySpec(altPublicKeyInfo.getEncoded()));
        } catch (InvalidKeySpecException | IOException e) {
            throw new CertificateRequestException("Cannot get alternative public key from certificate request.", e);
        }
    }

    @Override
    public Map<String, List<String>> getSubjectAlternativeNames() {
        return CertificateUtil.getSAN(this);
    }

    @Override
    public AlgorithmIdentifier getSignatureAlgorithm() {
        return jcaObject.getSignatureAlgorithm();
    }

    @Override
    public AlgorithmIdentifier getAltSignatureAlgorithm() {
        Extensions requestedExtensions = jcaObject.getRequestedExtensions();
        if (requestedExtensions == null) return null;
        Extension extension = requestedExtensions.getExtension(Extension.altSignatureAlgorithm);
        return extension == null ? null : AlgorithmIdentifier.getInstance(extension.getParsedValue());
    }

}
