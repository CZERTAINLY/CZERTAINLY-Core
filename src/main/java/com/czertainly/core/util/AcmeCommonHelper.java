package com.czertainly.core.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Class contains the common operations and helper functions to process the acme request
 */
public class AcmeCommonHelper {
    public static String keyAuthorizationGenerator(String token, String publicKey) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(publicKey.getBytes(StandardCharsets.UTF_8));
        return token + "." + Base64.getEncoder().encodeToString(hash).replace("=","");
    }
}
