package com.otilm.core.cbom.asset.identity;

/**
 * Renders a value into one slot of an identity pre-image, percent-escaping the delimiter set.
 *
 * <p>
 * The hazard is concrete, not theoretical. A producer-controlled {@code relatedCryptoMaterialProperties.id} of
 * {@code abc|F|x} produces the pre-image {@code MAT|secret-key|I|abc|F|x} -- which is exactly the shape a
 * fingerprint-tier asset emits, so a sloppy or hostile producer could forge a collision with a different tier of the
 * chain. Escaping is lossless, so no two distinct values are ever conflated.
 *
 * <p>
 * <b>Outer slots only.</b> A value that lands in a slot of the pre-image is escaped; a value that goes into a string
 * which is then <em>hashed</em>, with only the digest reaching a slot, is left literal. The distinguished-name
 * composite is the worked example: {@code CRT|S|v1|...} carries {@code 2.5.4.3=example%20ca} because that DN sits in an
 * outer slot, while the composite's inner pre-image carries {@code 2.5.4.3=vector ca} with the space intact, because
 * only its SHA-256 enters a slot. Getting this backwards re-keys every certificate.
 */
public final class PreImageSlot {

    private PreImageSlot() {
    }

    /** An absent value renders as the empty slot, which is distinct from every present value. */
    public static String of(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = null;
        for (int index = 0; index < value.length(); index++) {
            String replacement = escapeFor(value.charAt(index));
            if (replacement == null) {
                if (escaped != null) {
                    escaped.append(value.charAt(index));
                }
                continue;
            }
            if (escaped == null) {
                escaped = new StringBuilder(value.length() + 8).append(value, 0, index);
            }
            escaped.append(replacement);
        }
        return escaped == null ? value : escaped.toString();
    }

    public static String of(Integer value) {
        return value == null ? "" : value.toString();
    }

    private static String escapeFor(char character) {
        return switch (character) {
            case '%' -> "%25";
            case '|' -> "%7C";
            case ' ' -> "%20";
            case '\t' -> "%09";
            case '\r' -> "%0D";
            case '\n' -> "%0A";
            default -> null;
        };
    }
}
