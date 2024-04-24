package com.czertainly.core.model.request;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;

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
    PublicKey getPublicKey();

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
