package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Where an asset was seen, reduced to something that can enter a key without carrying a credential into it.
 *
 * <p>
 * The discriminator exists because location alone is not enough. Measured on one producer's scan, 33 distinct secret
 * keys occupy only 21 distinct location sets -- one source file holds five of them -- so a location-only discriminator
 * silently merges twelve different secrets. Adding line and offset yields 30 rows, and the three that still merge are
 * byte-identical in every field except a random UUID, so merging those is correct.
 */
public final class Occurrences {

    /** Everything from the first {@code ?} or {@code #} onward: query strings and fragments carry session tokens. */
    private static final Pattern QUERY_OR_FRAGMENT = Pattern.compile("[?#]");

    /** {@code scheme://user:pass@host} -- a real shape, and the reason a raw location must never be hashed. */
    private static final Pattern USERINFO = Pattern.compile("://[^/@]*@");

    private static final int MAX_LOCATION_LENGTH = 1024;

    private Occurrences() {
    }

    /**
     * SHA-256 over the sorted {@code location#line#offset} triples, or {@code null} when there are none.
     *
     * @see #triples for why the hashed string is exposed separately
     */
    public static String discriminator(JsonNode component) {
        String triples = triples(component);
        return triples == null ? null : Digests.sha256Hex(triples);
    }

    /**
     * The exact newline-joined string {@link #discriminator} hashes.
     *
     * <p>
     * Exposed as a named operation because a hashed slot is invisible to a conformance vector: the vector shows the
     * digest, so an implementer who computes a different string sees only that "the key differs" with no way to find
     * out why. The sibling case cost one proof-of-concept round 768 guesses for exactly this reason.
     */
    public static String triples(JsonNode component) {
        JsonNode evidence = component == null ? null : component.get("evidence");
        JsonNode occurrences = evidence == null ? null : evidence.get("occurrences");
        if (occurrences == null || !occurrences.isArray()) {
            return null;
        }
        TreeSet<String> sorted = new TreeSet<>();
        List<String> triples = new ArrayList<>();
        for (JsonNode occurrence : occurrences) {
            if (!occurrence.isObject()) {
                continue;
            }
            triples
                    .add(sanitizeLocation(occurrence.get("location")) + "#" + slot(occurrence.get("line")) + "#"
                            + slot(occurrence.get("offset")));
        }
        if (triples.isEmpty()) {
            return null;
        }
        sorted.addAll(triples);
        return String.join("\n", sorted);
    }

    /**
     * Strips credentials and volatile parts from an occurrence location.
     *
     * <p>
     * {@code tcp://user:pass@host:443} is a real shape, and the location feeds the identity key for version-less
     * protocols and identity-less material -- so unsanitized, a password would be hashed into the key and stored in the
     * evidence payload. The query and fragment go too: they carry session tokens and do not identify a location.
     */
    public static String sanitizeLocation(JsonNode location) {
        if (location == null || !location.isTextual() || AsciiText.isBlank(location.textValue())) {
            return "";
        }
        String text = AsciiText.strip(location.textValue());
        text = text.substring(0, capBoundary(text));
        text = QUERY_OR_FRAGMENT.split(text, 2)[0];
        return USERINFO.matcher(text).replaceFirst("://");
    }

    /**
     * Where the length cap may cut, which is never between the halves of a surrogate pair.
     *
     * <p>
     * The cap counts UTF-16 units, so a location of 1023 basic-plane characters followed by any astral character -- an
     * emoji or a CJK extension character in a scanned path is enough -- left a lone high surrogate as the last char.
     * That is well-formed input made malformed by the cap, and {@link Digests#sha256Hex} refuses it, so the component
     * became a reported skip and vanished from the inventory with nothing an operator could act on. The same truncated
     * string is written to stored evidence, where a lone surrogate has no valid UTF-8 encoding for a jsonb column.
     */
    private static int capBoundary(String text) {
        int end = Math.min(text.length(), MAX_LOCATION_LENGTH);
        return end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1)) ? end - 1 : end;
    }

    /**
     * Renders a line or offset into its position of the triple.
     *
     * <p>
     * Escaped like any other slot value, because a producer controls it and a crafted string could otherwise forge a
     * triple boundary.
     */
    private static String slot(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return "";
        }
        if (value.isNumber()) {
            return KeySlot.of(value.isIntegralNumber() ? value.bigIntegerValue().toString() : value.asText());
        }
        return KeySlot.of(value.asText());
    }
}
