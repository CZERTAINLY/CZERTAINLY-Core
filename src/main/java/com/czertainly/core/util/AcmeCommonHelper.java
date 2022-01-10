package com.czertainly.core.util;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Class contains the common operations and helper functions to process the acme request
 */
public class AcmeCommonHelper {
    public static String keyAuthorizationGenerator(String token, String publicKey) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(publicKey.getBytes(StandardCharsets.UTF_8));
        return token + "." + Base64.getEncoder().encodeToString(hash).replace("=","");
    }

    public static String createKeyAuthorization(String token, PublicKey publicKey) throws IllegalArgumentException {
        try {
            return token + "." + getJwkFromPublicKey(publicKey).computeThumbprint("SHA-256").toString();
        } catch (JOSEException var3) {
            throw new IllegalArgumentException(var3);
        }
    }

    public static JWK getJwkFromPublicKey(PublicKey publicKey) throws IllegalArgumentException {
        if (publicKey instanceof ECPublicKey) {
            return (new ECKey.Builder(Curve.P_256, (ECPublicKey)publicKey)).build();
        } else if (publicKey instanceof RSAPublicKey) {
            return (new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey)publicKey)).build();
        } else {
            throw new IllegalArgumentException("Unsupported public key type.");
        }
    }

    private Map<String, Object> generateJWK(PublicKey publicKey){
        RSAPublicKey rsa = (RSAPublicKey) publicKey;
        Map<String, Object> values = new HashMap<>();
        values.put("kty", rsa.getAlgorithm()); // getAlgorithm() returns kty not algorithm
        values.put("n",Base64.getUrlEncoder().encodeToString(rsa.getModulus().toByteArray()));
        values.put("e",Base64.getUrlEncoder().encodeToString(rsa.getPublicExponent().toByteArray()));
        return values;
    }
}
