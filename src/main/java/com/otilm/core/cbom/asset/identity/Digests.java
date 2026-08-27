package com.otilm.core.cbom.asset.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 over UTF-8, rendered lowercase hex -- the one digest form every tier of the identity chain uses. */
public final class Digests {

    private Digests() {
    }

    public static String sha256Hex(String text) {
        return HexFormat.of().formatHex(digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    public static String sha256HexOfBytes(byte[] bytes) {
        return HexFormat.of().formatHex(digest(bytes));
    }

    private static byte[] digest(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JCA specification; its absence is a broken JRE, not a runtime condition.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
