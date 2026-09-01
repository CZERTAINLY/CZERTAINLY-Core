package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    private static final Pattern USERINFO = Pattern.compile("://[^/?#]*@");

    private static final int MAX_LOCATION_LENGTH = 1024;

    private static final Comparator<String> CODE_POINT_ORDER = Occurrences::compareCodePoints;

    private Occurrences() {
    }

    /**
     * SHA-256 over the sorted {@code location#line#offset} triples, or {@code null} when there are none.
     *
     * @see #triples for why the hashed string is exposed separately
     */
    public static String discriminator(JsonNode component) {
        String triples = triples(component);
        return triples == null ? null : IdentityDigests.sha256Hex(triples);
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
        triples.sort(CODE_POINT_ORDER);
        return String.join("\n", triples);
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
        return sanitizeLocation(location.textValue());
    }

    public static String sanitizeLocation(String location) {
        if (AsciiText.isBlank(location)) {
            return "";
        }
        String text = AsciiText.strip(location);
        text = QUERY_OR_FRAGMENT.split(text, 2)[0];
        text = USERINFO.matcher(text).replaceFirst("://");
        return text.substring(0, capBoundary(text));
    }

    /**
     * Where the length cap may cut, counting code points rather than UTF-16 code units.
     *
     * <p>
     * The specification and the reference count characters; Java's {@code length()} counts UTF-16 storage units. The
     * two disagree from the first astral character onward, so a location of 1024 emoji was capped at 512 here and at
     * 1024 by the reference -- one location, two keys. Cutting on a code-point boundary also makes the lone-surrogate
     * case impossible rather than repaired: the old boundary check existed only because the unit count could land
     * between the halves of a pair.
     *
     * <p>
     * The cap is the last step of {@link #sanitizeLocation}, after the query, fragment and user-info have already gone,
     * so it can neither expose nor preserve a credential -- only shorten a location that no longer carries one.
     */
    private static int capBoundary(String text) {
        return text.codePointCount(0, text.length()) <= MAX_LOCATION_LENGTH
                ? text.length()
                : text.offsetByCodePoints(0, MAX_LOCATION_LENGTH);
    }

    /**
     * A numeric position that names no line or offset, kept distinct from the empty slot.
     *
     * <p>
     * {@code %3F} cannot arise from a producer value: {@link PreImageSlot} emits only {@code %25}, {@code %7C},
     * {@code %20}, {@code %09}, {@code %0D} and {@code %0A}, and escapes any literal {@code %} to {@code %25} first.
     * Rendering a refusal as the empty string instead would key {@code line: 1.5} identically to a stated-nothing
     * occurrence, and the empty slot means absent everywhere else in the chain.
     */
    private static final String REFUSED_POSITION = "%3F";

    /**
     * Renders a line or offset into its position of the triple.
     *
     * <p>
     * Escaped like any other slot value, because a producer controls it and a crafted string could otherwise forge a
     * triple boundary.
     *
     * <p>
     * A non-integral number has no exact integer to render, so it is refused rather than rounded or spelled out --
     * {@code JsonNode.asText()} on {@code 1.5} keys on the producer's serializer. Refusal is not absence, so it renders
     * as {@link #REFUSED_POSITION}.
     */
    private static String slot(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return "";
        }
        if (value.isNumber()) {
            return value.isIntegralNumber() ? PreImageSlot.of(value.bigIntegerValue().toString()) : REFUSED_POSITION;
        }
        return PreImageSlot.of(value.asText());
    }

    private static int compareCodePoints(String left, String right) {
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            int leftCodePoint = left.codePointAt(leftIndex);
            int rightCodePoint = right.codePointAt(rightIndex);
            if (leftCodePoint != rightCodePoint) {
                return Integer.compare(leftCodePoint, rightCodePoint);
            }
            leftIndex += Character.charCount(leftCodePoint);
            rightIndex += Character.charCount(rightCodePoint);
        }
        return Integer.compare(left.length() - leftIndex, right.length() - rightIndex);
    }
}
