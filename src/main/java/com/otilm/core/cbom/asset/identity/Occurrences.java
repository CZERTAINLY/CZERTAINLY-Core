package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
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

    /**
     * {@code scheme://user:pass@host} -- a real shape, and the reason a raw location must never be hashed.
     *
     * <p>
     * Replaced <b>globally</b>, not once. One location can hold more than one URI: an archive scanner writes
     * {@code jar:file://u:p@h/a.jar!/https://u2:p2@h2/b} and a Kafka bootstrap list is comma-separated. Because
     * {@code [^/?#]*} cannot cross a {@code /}, a single replacement leaves every credential after the first standing
     * -- and this is the one method in this package on the live path to the served {@code evidence} column, so what
     * survives here is a stored, queryable secret.
     */
    private static final Pattern USERINFO = Pattern.compile("://[^/?#]*@");

    /**
     * An unpaired surrogate, which is well-formed to Java and has no encoding at all in UTF-8.
     *
     * <p>
     * {@link IdentityDigests#sha256Hex} refuses one, so a component carrying it becomes a reported skip and vanishes
     * from the inventory; the same string also has no valid encoding for the {@code jsonb} evidence column. Scrubbing
     * is unconditional rather than a cap-boundary repair, because a producer can put one anywhere in the string and
     * only the cut position was ever guarded.
     */
    private static final Pattern UNPAIRED_SURROGATE = Pattern
            .compile("[\\uD800-\\uDBFF](?![\\uDC00-\\uDFFF])" + "|(?<![\\uD800-\\uDBFF])[\\uDC00-\\uDFFF]");

    private static final int MAX_LOCATION_LENGTH = 1024;

    private static final Comparator<String> CODE_POINT_ORDER = AsciiText.BY_CODE_POINT;

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

    /**
     * Sanitizes in an order the steps cannot undo for each other.
     *
     * <p>
     * <b>The surrogate scrub goes first.</b> Removing a lone surrogate can <em>create</em> the {@code ://...@} shape
     * that {@link #USERINFO} strips: {@code x:\uD800//user:pass@host} does not match the pattern, and scrubbing after
     * the strip yields {@code x://user:pass@host} -- a well-formed, hashable location with the credential intact.
     * Scrubbing first cannot have the mirror effect, because deleting a surrogate introduces no {@code ?}, {@code #},
     * {@code @} or {@code :} for a later step to miss.
     */
    public static String sanitizeLocation(String location) {
        if (AsciiText.isBlank(location)) {
            return "";
        }
        String text = AsciiText.strip(location);
        text = UNPAIRED_SURROGATE.matcher(text).replaceAll("");
        text = withoutQueryOrFragment(text);
        text = USERINFO.matcher(text).replaceAll("://");
        return text.substring(0, capBoundary(text));
    }

    /**
     * The location up to its first {@code ?} or {@code #}, unless the delimiter is the first character.
     *
     * <p>
     * A location that <em>begins</em> with the delimiter is all fragment, and cutting at position zero rendered it as
     * the empty string -- which is what an absent location renders as. A CycloneDX occurrence inside an OpenAPI or JSON
     * document carries a JSON pointer, so {@code #/components/schemas/PrivateKey} and
     * {@code #/components/schemas/PublicKey} both became the empty location and then shared one discriminator with each
     * other and with every component that stated no location at all.
     *
     * <p>
     * Such a location keeps its own text instead. It states something, and the query-and-fragment rule exists to drop a
     * <em>trailing</em> session token from a real path, not to erase a pointer that is the whole reference.
     *
     * <p>
     * <b>Only a leading {@code #} earns that.</b> A pointer keeps its text but still loses a query of its own, and a
     * location beginning with {@code ?} names no place at all -- it is a bare query string, so keeping it verbatim
     * would store exactly the session token this rule exists to drop. That one renders as the empty location, which is
     * what stating no location renders as, because it states no location.
     */
    private static String withoutQueryOrFragment(String text) {
        String[] halves = QUERY_OR_FRAGMENT.split(text, 2);
        if (!halves[0].isEmpty()) {
            return halves[0];
        }
        if (text.charAt(0) != '#') {
            return "";
        }
        return "#" + QUERY_OR_FRAGMENT.split(text.substring(1), 2)[0];
    }

    /**
     * Where the length cap may cut, counting code points rather than UTF-16 code units.
     *
     * <p>
     * The specification and the reference count characters; Java's {@code length()} counts UTF-16 storage units. The
     * two disagree from the first astral character onward, so a location of 1024 emoji was capped at 512 here and at
     * 1024 by the reference -- one location, two keys.
     *
     * <p>
     * Cutting on a code-point boundary means the cap can no longer <em>create</em> a lone surrogate, which is the case
     * the old positional guard was written for. It says nothing about one already present in the producer's text, which
     * the cap can now carry through where the old boundary sometimes happened to trim it -- so
     * {@link #UNPAIRED_SURROGATE} scrubs those explicitly rather than relying on where the cut lands.
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
     * Escaped like any other slot value, because a producer controls it. Note what that does and does not buy:
     * {@link PreImageSlot} escapes {@code %}, {@code |}, space, tab, CR and LF, but <b>not</b> {@code #}, which is this
     * triple's own delimiter. So a textual position can still move the boundary between the line and offset fields --
     * {@code line="1#2", offset="3"} and {@code line="1", offset="2#3"} both render {@code a#1#2#3}. The producer
     * states all three fields either way, and the schema types line and offset as integers so a textual value is
     * schema-invalid, but the escaping is not what prevents it.
     *
     * <p>
     * A number is rendered through its exact decimal value, so {@code 1.0} and {@code 2.0} stay apart and {@code 1e3}
     * renders as the line {@code 1000} it names. {@code isIntegralNumber} was the wrong test: it asks Jackson's node
     * type, not the value, so every double-serialized line collapsed onto one refusal -- and a producer whose JSON
     * writer emits {@code 1.0} for an integer had its discriminator degraded to location-only.
     *
     * <p>
     * Only a genuinely fractional position has no line to name, and that renders as {@link #REFUSED_POSITION} rather
     * than as the empty slot, because refusal is not absence.
     */
    private static String slot(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return "";
        }
        if (value.isNumber()) {
            // The sentinel bypasses PreImageSlot deliberately. Escaping it would render it %253F, which is exactly
            // what a producer spelling "%3F" renders as -- restoring the collision the sentinel exists to avoid.
            String exact = exactPosition(value);
            return exact == null ? REFUSED_POSITION : PreImageSlot.of(exact);
        }
        return PreImageSlot.of(value.asText());
    }

    /**
     * A numeric position as an exact integer, or {@code null} when it names no integer at all.
     *
     * <p>
     * {@code stripTrailingZeros().toPlainString()} is exact and JDK-stable, which {@code asText()} is not:
     * {@code JsonNode.asText()} on a large integral node can yield exponent notation, keying one line two ways
     * depending on how the producer serialized it.
     */
    private static String exactPosition(JsonNode value) {
        if (value.isIntegralNumber()) {
            return value.bigIntegerValue().toString();
        }
        if (value.isFloatingPointNumber() && !Double.isFinite(value.doubleValue())) {
            // A JSON 1e999 parses to Infinity, and BigDecimal.valueOf(Infinity) throws. The sentinel exists so an
            // unusable position is refused rather than fatal, so the overflow spelling has to reach it, not bypass it.
            return null;
        }
        BigDecimal exact = value.decimalValue().stripTrailingZeros();
        return exact.scale() <= 0 ? exact.toPlainString() : null;
    }
}
