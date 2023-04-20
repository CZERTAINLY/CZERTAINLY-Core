package com.czertainly.core.util;

import java.security.NoSuchAlgorithmException;

public class AlgorithmUtil {

    public static String getSignatureAlgorithmName(String digestAlgorithmOID, String privateKeyAlgorithm) throws NoSuchAlgorithmException {
        // Map digest algorithm OID to digest algorithm name
        String digestAlgorithm = getDigestAlgorithm(digestAlgorithmOID);
        // Determine signature algorithm name based on private key algorithm and digest algorithm
        String signatureAlgorithm;
        if (privateKeyAlgorithm.equals("RSA")) {
            signatureAlgorithm = digestAlgorithm + "withRSA";
        } else if (privateKeyAlgorithm.equals("EC")) {
            signatureAlgorithm = digestAlgorithm + "withECDSA";
        } else {
            throw new NoSuchAlgorithmException("Unsupported private key algorithm: " + privateKeyAlgorithm);
        }

        return signatureAlgorithm;
    }

    public static String getDigestAlgorithm(String digestAlgorithmOID) throws NoSuchAlgorithmException {
        if (digestAlgorithmOID.equals("1.2.840.113549.1.1.5")) { // SHA1 OID
            return "SHA-1";
        } else if (digestAlgorithmOID.equals("2.16.840.1.101.3.4.2.1")) { // SHA256 OID
            return "SHA-256";
        } else if (digestAlgorithmOID.equals("2.16.840.1.101.3.4.2.2")) { // SHA384 OID
            return "SHA-384";
        } else if (digestAlgorithmOID.equals("2.16.840.1.101.3.4.2.3")) { // SHA512 OID
            return "SHA-512";
        } else {
            throw new NoSuchAlgorithmException("Unsupported digest algorithm OID: " + digestAlgorithmOID);
        }
    }

}
