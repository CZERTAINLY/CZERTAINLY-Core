package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.TreeSet;

/**
 * Cipher-suite identifiers, reduced to IANA codes -- never bom-refs, never raw suite names.
 *
 * <p>
 * A suite's {@code algorithms[]} holds document-internal bom-refs, and the same AES-128-GCM asset is
 * {@code ...@sha256:eba74aba...} in one document and {@code ...@b0c6374d-...} in another, so hashing them forks one
 * estate into two rows. The suite {@code name} is not producer-stable either: one producer emits the OpenSSL alias
 * {@code TLS_AKE_WITH_AES_128_GCM_SHA256} where IANA calls code {@code 0x1301} {@code TLS_AES_128_GCM_SHA256}. The
 * identifiers are the code bytes, so they are stable.
 */
public final class CipherSuites {

    private CipherSuites() {
    }

    /**
     * Normalizes an identifier list to its IANA code as lowercase hex, or {@code null}.
     *
     * <p>
     * Three encodings occur in real documents and all three must land on the same code: one element per byte unpadded
     * ({@code ["0x13", "0x1"]}), one element per byte padded ({@code ["0xC0", "0x30"]}), and both bytes comma-packed
     * into a single element ({@code "0x13,0x02"}).
     *
     * <p>
     * Bytes are joined in <b>array order, before any sorting</b>, because flattening across suites collides:
     * {@code {0xC02B, 0x1301}} and {@code {0xC001, 0x132B}} share a byte multiset and would otherwise hash alike.
     */
    public static String code(JsonNode identifiers) {
        if (identifiers == null || !identifiers.isArray()) {
            return null;
        }
        StringBuilder octets = new StringBuilder();
        for (JsonNode element : identifiers) {
            if (!element.isTextual()) {
                // Skipping it instead let ["0x13", {}, "0x01"] produce the code of the well-formed ["0x13", "0x01"],
                // so a malformed list impersonated a real suite. All-or-nothing means the malformed element decides.
                return null;
            }
            for (String token : element.textValue().split(",")) {
                String trimmed = AsciiText.strip(token);
                if (trimmed.isEmpty()) {
                    continue;
                }
                String digits = AsciiText.fold(trimmed).startsWith("0x") ? trimmed.substring(2) : trimmed;
                try {
                    octets.append(String.format("%02x", Integer.parseInt(digits, 16)));
                } catch (NumberFormatException e) {
                    // A list this implementation cannot read yields no code at all rather than a partial one. The
                    // caller falls back to the suite name, which is what keeps "we know it has suites and cannot read
                    // them" apart from "nothing was said".
                    return null;
                }
            }
        }
        return octets.isEmpty() ? null : octets.toString();
    }

    /**
     * The exact newline-joined string the cipher-suite slot hashes, or {@code null} when no suite yields a token.
     *
     * <p>
     * Exposed for the same reason as the occurrence triples: a hashed slot that does not publish its input is not
     * testable, because a vector shows only the digest.
     *
     * <p>
     * A suite whose code was refuted by the document falls back to its uppercased name rather than vanishing. That
     * fallback is also what makes a name-emitting producer split visibly from a code-emitting one.
     */
    public static String tokens(JsonNode properties, Set<String> refuted) {
        JsonNode protocol = properties == null ? null : properties.get("protocolProperties");
        JsonNode suites = protocol == null ? null : protocol.get("cipherSuites");
        if (suites == null || !suites.isArray()) {
            return null;
        }
        TreeSet<String> tokens = new TreeSet<>();
        for (JsonNode suite : suites) {
            if (!suite.isObject()) {
                continue;
            }
            String code = code(suite.get("identifiers"));
            JsonNode name = suite.get("name");
            if (code != null && !refuted.contains(code)) {
                tokens.add(code);
            } else if (name != null && name.isTextual() && !AsciiText.isBlank(name.textValue())) {
                tokens.add(AsciiText.upper(AsciiText.strip(name.textValue())));
            }
        }
        return tokens.isEmpty() ? null : String.join("\n", tokens);
    }

    /** SHA-256 over {@link #tokens}, or {@code null} when no suite yielded one. */
    public static String digest(JsonNode properties, Set<String> refuted) {
        String tokens = tokens(properties, refuted);
        return tokens == null ? null : IdentityDigests.sha256Hex(tokens);
    }

    /** True when the properties declare a {@code cipherSuites} array with at least one entry. */
    public static boolean declared(JsonNode properties) {
        JsonNode protocol = properties == null ? null : properties.get("protocolProperties");
        JsonNode suites = protocol == null ? null : protocol.get("cipherSuites");
        return suites != null && suites.isArray() && suites.size() > 0;
    }
}
