package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

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

    private static final Pattern HEX_DIGITS = Pattern.compile("[0-9a-f]+");

    private static final int MAX_CODE_UNIT = 0xFFFF;

    private CipherSuites() {
    }

    /**
     * Normalizes an identifier list to its IANA code as lowercase hex, or {@code null}.
     *
     * <p>
     * Four encodings occur in real documents and all four must land on the same code: one element per byte unpadded
     * ({@code ["0x13", "0x1"]}), one element per byte padded ({@code ["0xC0", "0x30"]}), both bytes comma-packed into a
     * single element ({@code "0x13,0x02"}), and the whole two-byte code in one token ({@code ["0x1301"]}, which
     * CycloneDX's own {@code valid-cryptography-full-1.7} conformance fixture emits). Rendering each token to an even
     * hex width is what merges them; a one-octet parser splits that fixture's TLS 1.3 suite from every per-byte
     * spelling of it.
     *
     * <p>
     * Bytes are joined in <b>array order, before any sorting</b>, because flattening across suites collides:
     * {@code {0xC02B, 0x1301}} and {@code {0xC001, 0x132B}} share a byte multiset and would otherwise hash alike.
     *
     * <p>
     * <b>Grouping is not identity.</b> Because every token renders to a whole number of octets, the result is the
     * declaration's <em>byte stream</em> and nothing else: {@code ["0x131", "0x1"]} and {@code ["0x01", "0x3101"]} both
     * render {@code 013101} because both state the bytes {@code 01 31 01}. That is the same equivalence that merges the
     * four spellings above, not a collision beside it -- two lists keying alike means they said the same bytes. What
     * would be a defect is a token rendering to half an octet, which is what the unpadded {@code %02x} did to
     * {@code 0x131}: {@code 131} is not a byte stream at all, so the concatenation stopped meaning anything.
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
            if (!appendOctets(element.textValue(), octets)) {
                return null;
            }
        }
        return octets.isEmpty() ? null : octets.toString();
    }

    /** Appends every token of one element, or returns {@code false} for a list this implementation cannot read. */
    private static boolean appendOctets(String element, StringBuilder octets) {
        for (String token : element.split(",")) {
            String trimmed = AsciiText.strip(token);
            if (trimmed.isEmpty()) {
                continue;
            }
            String hex = octetsOf(AsciiText.fold(trimmed));
            if (hex == null) {
                return false;
            }
            octets.append(hex);
        }
        return true;
    }

    /**
     * One folded token as an even number of lowercase hex digits, or {@code null} when it is not a readable code unit.
     *
     * <p>
     * A token this implementation cannot read yields no code for the whole list rather than a partial one. The caller
     * falls back to the suite name, which is what keeps "we know it has suites and cannot read them" apart from
     * "nothing was said".
     *
     * @param folded a non-empty token, already ASCII-folded by the caller -- the case fold stays there because that is
     * where the token is known to be present, and this method is about hex rather than about case
     */
    private static String octetsOf(String folded) {
        if (folded.startsWith("+") || folded.startsWith("-")) {
            return null;
        }
        String digits = folded.startsWith("0x") ? folded.substring(2) : folded;
        if (!HEX_DIGITS.matcher(digits).matches()) {
            return null;
        }
        try {
            int value = Integer.parseInt(digits, 16);
            if (value > MAX_CODE_UNIT) {
                return null;
            }
            String hex = Integer.toHexString(value);
            return hex.length() % 2 == 0 ? hex : "0" + hex;
        } catch (NumberFormatException e) {
            // More hex digits than an int holds. HEX_DIGITS already passed, so this is length alone.
            return null;
        }
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
                tokens.add("c:" + code);
            } else if (name != null && name.isTextual() && !AsciiText.isBlank(name.textValue())) {
                tokens.add("n:" + PreImageSlot.of(AsciiText.upper(AsciiText.strip(name.textValue()))));
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
