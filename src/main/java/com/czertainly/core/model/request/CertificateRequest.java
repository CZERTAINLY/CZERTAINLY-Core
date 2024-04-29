package com.czertainly.core.model.request;

import com.czertainly.api.exception.CertificateRequestException;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.openssl.PEMException;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Map;

/**
 * Interface for certificate request
 */
public interface CertificateRequest {

    /**
     * Get encoded request
     *
     * @return encoded request
     */
    byte[] getEncoded();

    /**
     * Get subject of the request as X500Name
     *
     * @return subject
     */
    X500Name getSubject();

    /**
     * Get the public key of the request
     *
     * @return public key
     */
    PublicKey getPublicKey() throws NoSuchAlgorithmException, CertificateRequestException;

    /**
     * Get the subject alternative names
     *
     * @return subject alternative names
     */
    Map<String, Object> getSubjectAlternativeNames();

    /**
     * Get the signature algorithm
     *
     * @return signature algorithm
     */
    AlgorithmIdentifier getSignatureAlgorithm();

}
