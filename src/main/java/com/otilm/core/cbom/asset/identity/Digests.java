package com.otilm.core.cbom.asset.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 over UTF-8, rendered lowercase hex -- the one digest form every tier of the identity chain uses. */
public final class Digests {

    private Digests() {
    }

    /**
     * The digest of a pre-image, over its UTF-8 bytes.
     *
     * <p>
     * An unpaired surrogate is rejected rather than encoded. {@code String.getBytes(UTF_8)} substitutes {@code ?} for
     * one silently, which merges distinct producer strings onto a single pre-image: a lone high surrogate, a lone low
     * surrogate and a literal {@code ?} all hash identically. That is an identity collision reachable from a document
     * carrying a bare {@code \uD800} escape, which Jackson admits. The reference implementation raises here, so
     * refusing is also what keeps the two byte-identical; unpaired surrogates are not JSON text (RFC 8259 section 8.2),
     * so no well-formed document reaches this. The component becomes a reported skip, not a failed walk.
     */
    public static String sha256Hex(String text) {
        requireWellFormedUnicode(text);
        return HexFormat.of().formatHex(digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    public static String sha256HexOfBytes(byte[] bytes) {
        return HexFormat.of().formatHex(digest(bytes));
    }

    private static void requireWellFormedUnicode(String text) {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 == text.length() || !Character.isLowSurrogate(text.charAt(index + 1))) {
                    throw new IllegalArgumentException("An identity pre-image carries an unpaired surrogate");
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                throw new IllegalArgumentException("An identity pre-image carries an unpaired surrogate");
            }
        }
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
