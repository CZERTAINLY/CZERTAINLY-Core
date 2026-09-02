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

    /**
     * Everything from the first {@code ?} or {@code #} onward: query strings and fragments carry session tokens.
     *
     * <p>
     * A match at position zero is the one case where the text before the delimiter is not the answer -- see
     * {@link #withoutQueryOrFragment}, which keeps a leading fragment's <em>text</em> without keeping its delimiter.
     */
    private static final Pattern QUERY_OR_FRAGMENT = Pattern.compile("[?#]");

    /**
     * {@code scheme://user:pass@host} -- a real shape, and the reason a raw location must never be hashed.
     *
     * <p>
     * Replaced <b>globally</b>, not once. One location can hold more than one URI: an archive scanner writes
     * {@code jar:file://u:p@h/a.jar!/https://u2:p2@h2/b} and a Kafka bootstrap list is comma-separated. A single
     * replacement leaves every credential after the first standing -- and this is the one method in this package on the
     * live path to the served {@code evidence} column, so what survives here is a stored, queryable secret.
     *
     * <p>
     * <b>{@code [^/]*}, not {@code [^/?#]*}.</b> Excluding the two delimiters from the user-info class made the pattern
     * blind to a credential that contains one: {@code //user:sec?ret@host/x} has its {@code @} beyond a {@code ?}, so
     * {@code [^/?#]*@} matched nothing and {@link #withoutQueryOrFragment} then kept {@code //user:sec} -- a stored
     * password prefix, whole when the password itself ends in {@code ?}. No step order can repair that: the class
     * cannot span a delimiter it excludes, so the class is what had to widen. The cost is an authority that carries a
     * genuine query: {@code //host?to=a@b} loses the host along with the credential shape, which is fidelity spent to
     * close a credential leak.
     *
     * <p>
     * <b>Unanchored.</b> The pattern used to require the {@code //} at the string start or after a colon, which the
     * leading-fragment retention in {@link #withoutQueryOrFragment} silently defeated: cutting the {@code #} off
     * {@code #/api/v1//admin:hunter2@db.internal/x} moves the {@code //} into the middle of a path, where an anchored
     * pattern cannot see it, and the credential reached both the key and the evidence column. A path-internal
     * {@code //u:p@h} is not a URI authority, but it is a credential either way, and this class strips credentials.
     *
     * <p>
     * {@code [^/]*} stays <b>greedy</b> on purpose. A comma-separated multi-host authority --
     * {@code mongodb://u1:p1@h1,u2:p2@h2/db} -- carries two credentials under one {@code //}, and a lazy quantifier
     * stops at the first {@code @} and leaves the second standing. Greedy costs the first host, which is fidelity; lazy
     * costs a credential, which is the thing this pattern exists for.
     */
    private static final Pattern USERINFO = Pattern.compile("//[^/]*@");

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
            .compile("(?:[\\uD800-\\uDBFF](?![\\uDC00-\\uDFFF]))" + "|(?:(?<![\\uD800-\\uDBFF])[\\uDC00-\\uDFFF])");

    /**
     * The triple's other separator, which the location slot cannot escape.
     *
     * <p>
     * {@link #triples} joins triples with a newline and the location is the one slot {@link PreImageSlot} does not
     * escape, so a location carrying CR or LF is the only value in the chain that can render as more than one line. A
     * forged line cannot pass for a triple -- every real one carries two {@code #} and no sanitized location can carry
     * any -- but the value also reaches the served {@code evidence} column, and a line break names no place. Removed
     * rather than escaped, for the same reason the {@code #} is cut rather than added to the escape set: the set is
     * shared, and opening it re-keys every row that contains one of its members.
     */
    private static final Pattern LINE_BREAK = Pattern.compile("[\r\n]");

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
     * evidence payload. A query string goes too, wherever it sits: it carries session tokens and identifies no
     * location. A fragment goes with it, except when the location <em>is</em> the fragment -- a JSON pointer states a
     * place, and {@link #withoutQueryOrFragment} keeps its text without its delimiter.
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
     *
     * <p>
     * <b>The user-info strip runs on both sides of the cut, and that is not belt-and-braces.</b> Before the cut it sees
     * a credential whose {@code @} lies beyond a {@code ?} or {@code #}, which the cut would otherwise truncate into a
     * stored password prefix. After the cut it sees the authority that the leading-fragment retention uncovers, which
     * was not an authority before the {@code #} came off. Each pass closes a case the other cannot, and the second is a
     * no-op for every location that carried no delimiter.
     */
    public static String sanitizeLocation(String location) {
        if (AsciiText.isBlank(location)) {
            return "";
        }
        String text = AsciiText.strip(location);
        text = UNPAIRED_SURROGATE.matcher(text).replaceAll("");
        text = LINE_BREAK.matcher(text).replaceAll("");
        text = withoutUserInfo(text);
        text = withoutQueryOrFragment(text);
        text = withoutUserInfo(text);
        return text.substring(0, capBoundary(text));
    }

    /**
     * Strips user-info until nothing changes, which is not the same as stripping it once or twice.
     *
     * <p>
     * The replacement can <em>create</em> the shape it matches. {@code //u1:p1@/u2:p2@/u3:p3@host} has three
     * {@code @}-terminated authorities sharing their slashes: consuming {@code //u1:p1@} leaves {@code ///u2:p2@…},
     * where scanning resumes past the {@code /} and finds only one slash before the next credential. Each pass
     * therefore peels exactly one layer, so two passes left the third password in the key and in the served
     * {@code evidence} column. Found by an exhaustive sweep to length 8 over <code>{/ : @ ? # a p}</code> -- 27 of 6
     * 725 600 inputs, all of this one family -- after a hand-built case set missed it, which is the difference between
     * covering the cases you thought of and covering the input space.
     *
     * <p>
     * The loop terminates because every replacement removes at least the {@code @}, so the string strictly shortens.
     *
     * <p>
     * Two residuals stay, both pre-existing and both measured: a user-info containing a {@code /} survives whole
     * ({@code //u:pa/ss@host/x}), because {@code [^/]*} cannot cross the slash and widening it further would eat whole
     * paths; and a credential with no {@code //} at all ({@code smtp:user:pass@host}, an scp-style
     * {@code user:pass@host:/path}) is not an authority and is out of this pattern's scope.
     */
    private static String withoutUserInfo(String text) {
        String stripped = text;
        String previous = null;
        while (!stripped.equals(previous)) {
            previous = stripped;
            stripped = USERINFO.matcher(stripped).replaceAll("//");
        }
        return stripped;
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
     * Such a location keeps its own <em>text</em> instead, without its delimiter. It states something, and the
     * query-and-fragment rule exists to drop a <em>trailing</em> session token from a real path, not to erase a pointer
     * that is the whole reference.
     *
     * <p>
     * <b>The delimiter itself does not come back.</b> {@code #} is the separator of the occurrence triple and the
     * location slot is the one slot {@link PreImageSlot} does not escape, so a location that carries a {@code #} can
     * forge a triple: with a newline in the same string, one occurrence renders as two triple lines. Returning the text
     * whole re-opened that -- 234 colliding one-occurrence-against-two pairs over <code>{#, ?, \n, a, b, 1, 2}</code>,
     * none before. Cutting after the delimiter keeps the pointer distinct and restores the invariant that made a
     * newline harmless, without opening the escape set and re-keying every row.
     *
     * <p>
     * <b>Only a leading {@code #} earns retention at all.</b> A pointer states a place; a location beginning with
     * {@code ?} is a bare query string -- {@code ?X-Amz-Signature=}, {@code ?sig=}, {@code ?token=} are the shapes this
     * rule was written for -- so keeping it verbatim would store exactly the session token the rule drops. It renders
     * as the empty location. A sentinel distinguishable from absence would be better, as it is for a refused position,
     * but this slot is unescaped: any sentinel a location could carry, a producer can also spell.
     *
     * <p>
     * <b>The whole leading run comes off, not one character.</b> Removing exactly one {@code #} left {@code ##a}
     * cutting at position zero again, so it rendered as the empty location -- the defect this retention exists to
     * close, reappearing one character further along. What the run rule does <em>not</em> restore is the count:
     * {@code #a} and {@code ##a} both render {@code a}, because the delimiter cannot come back into an unescaped slot.
     * That is the same residual as a pointer keying alike to the bare path it spells, and it is recorded as an open
     * adjudication on core#2165 rather than papered over with a sentinel a producer could also spell.
     *
     * <p>
     * <b>An empty string reaches this method.</b> {@link #UNPAIRED_SURROGATE} runs first and {@link AsciiText#isBlank}
     * does not treat a lone surrogate as whitespace, so a location made only of surrogates passes the entry guard and
     * arrives here empty -- where reading its first character threw {@code StringIndexOutOfBoundsException} out of the
     * ingest path, taking the whole source upsert down with it. On {@code main} that same input reached
     * {@link IdentityDigests#sha256Hex} and became a reported skip, so the step order turned a diagnosable skip into an
     * index error.
     */
    private static String withoutQueryOrFragment(String text) {
        if (text.isEmpty()) {
            return "";
        }
        String[] halves = QUERY_OR_FRAGMENT.split(text, 2);
        if (!halves[0].isEmpty()) {
            return halves[0];
        }
        int retained = leadingDelimiterRun(text);
        if (text.charAt(0) != '#' || text.lastIndexOf('?', retained - 1) >= 0) {
            // A bare query string, or a fragment whose own text begins with one. Either way the first thing the
            // location states is a token, not a place.
            return "";
        }
        return QUERY_OR_FRAGMENT.split(text.substring(retained), 2)[0];
    }

    /** The length of the {@code [?#]} run the location opens with. */
    private static int leadingDelimiterRun(String text) {
        int index = 0;
        while (index < text.length() && (text.charAt(index) == '#' || text.charAt(index) == '?')) {
            index++;
        }
        return index;
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
     * A number is rendered through its value rather than its spelling, so {@code 1.0} and {@code 2.0} stay apart and
     * {@code 1e3} renders as the line {@code 1000} it names. {@code isIntegralNumber} was the wrong test: it asks
     * Jackson's node type, not the value, so every double-serialized line collapsed onto one refusal -- and a producer
     * whose JSON writer emits {@code 1.0} for an integer had its discriminator degraded to location-only.
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
     * <b>What the rendered string is.</b> {@code decimalValue()} on a double node is {@code BigDecimal.valueOf}, whose
     * contract is {@code new BigDecimal(Double.toString(d))} -- so the keyed string is the shortest decimal that round
     * trips, zero-expanded, not the value's exact binary expansion: {@code 1e23} keys as
     * {@code 100000000000000000000000} while the double it names is {@code 99999999999999991611392}. That is a narrower
     * dependency than {@code asText()}'s, which yielded exponent notation for a large integral node and keyed one line
     * two ways on the producer's serializer, but it is not none: {@code Double.toString}'s algorithm was rewritten in
     * JDK 19 (JDK-4511638). Using {@code new BigDecimal(double)} would remove it entirely at the cost of diverging from
     * the reference kernel's rendering, so the dependency is recorded rather than closed.
     *
     * <p>
     * A position that underflows to zero -- {@code 1e-1000} -- keys as the line {@code 0}, because it parses to the
     * double {@code 0.0} before this method sees it and nothing downstream can tell the two apart.
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
