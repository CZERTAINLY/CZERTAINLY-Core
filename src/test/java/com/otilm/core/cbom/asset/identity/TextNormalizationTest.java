package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The text primitives every keyed value passes through, and the places where Java's defaults are the wrong answer.
 *
 * <p>
 * Most of what is pinned here was caught by differential testing rather than by reading, and the cases share one shape:
 * a hashed value whose behaviour was inherited from a language primitive rather than decided. That is the failure class
 * this suite exists to freeze.
 *
 * <p>
 * Exotic code points are built with {@link Character#toString(int)} rather than written as literals. {@code U+0085 NEXT
 * LINE} reads as a line terminator to more than one Java source tool -- a file containing one literally cannot be
 * parsed by the formatter this project gates on, even though javac accepts it -- and a unicode escape does not help,
 * because escapes are resolved before the lexer runs.
 */
class TextNormalizationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ---------------------------------------------------------------- ASCII folding

    /**
     * Folding is ASCII-only, so a non-ASCII character can never alias onto a registry token.
     *
     * <p>
     * {@code U+212A KELVIN SIGN} case-folds to ASCII {@code k} under Unicode rules, which would let a crafted name
     * claim a family it does not have. Under this fold it stays itself.
     */
    @Test
    void aUnicodeLookalikeCannotAliasOntoARegistryToken() {
        String kelvinSign = Character.toString(0x212A);

        assertThat(AsciiText.fold(kelvinSign)).isEqualTo(kelvinSign);
        assertThat(AsciiText.fold("KEM")).isEqualTo("kem");
    }

    /** Upper-casing is ASCII-only too, because a Unicode upper-case is length-changing on non-ASCII input. */
    @Test
    void upperCasingIsLengthPreserving() {
        String sharpS = Character.toString(0x00DF);

        assertThat(AsciiText.upper(sharpS))
                .describedAs("sharp s upper-cases to SS under Unicode rules")
                .isEqualTo(sharpS);
        assertThat(AsciiText.upper("gcm")).isEqualTo("GCM");
    }

    /** A Turkish-locale JVM must not key differently from any other node in the cluster. */
    @Test
    void foldingDoesNotDependOnThePlatformLocale() {
        assertThat(AsciiText.fold("KYBER-I")).isEqualTo("kyber-i");
        assertThat(AsciiText.upper("kyber-i")).isEqualTo("KYBER-I");
    }

    @ParameterizedTest
    @CsvSource({"'A-E-S', aes", "'a e s', aes", "'A_E_S', aes", "'AES', aes", "'p-256', p256"})
    void lookupKeysDropSeparatorsAndCase(String input, String expected) {
        assertThat(AsciiText.lookupKey(input)).isEqualTo(expected);
    }

    // ---------------------------------------------------------------- whitespace

    /**
     * The whitespace stripped is the reference's, not the JDK's.
     *
     * <p>
     * Measured, the two definitions disagree on exactly these three code points, and {@link String#strip()} misses all
     * three because it consults {@link Character#isWhitespace}. The two no-break spaces are the ones that occur in
     * producer text pasted out of documents: a trailing one survives {@code strip()}, NFKC then turns it into an
     * ordinary trailing space, and {@code "RSA "} keys apart from {@code "RSA"} -- a silent inventory split on a
     * formatting accident.
     */
    @ParameterizedTest
    @ValueSource(ints = {0x0085, 0x00A0, 0x202F})
    void theThreeCodePointsJavaMissesAreStripped(int codePoint) {
        String character = Character.toString(codePoint);

        assertThat(Character.isWhitespace(codePoint))
                .describedAs("if this becomes true, the JDK changed and this rule can be simplified")
                .isFalse();
        assertThat(AsciiText.strip("RSA" + character)).isEqualTo("RSA");
        assertThat(AsciiText.isBlank(character)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {
            0x0020,
            0x0009,
            0x000A,
            0x000D,
            0x000B,
            0x000C,
            0x001C,
            0x1680,
            0x2000,
            0x200A,
            0x2028,
            0x2029,
            0x205F,
            0x3000})
    void theWhitespaceBothLanguagesAgreeOnIsStripped(int codePoint) {
        assertThat(AsciiText.strip("RSA" + Character.toString(codePoint))).isEqualTo("RSA");
    }

    /**
     * A zero-width space is whitespace to neither language, so it is <em>not</em> stripped.
     *
     * <p>
     * Pinned as a known limitation rather than as a desired behaviour. {@code "RSA"} followed by {@code U+200B} keys
     * apart from {@code "RSA"} in both implementations, which makes it a shared blind spot invisible to any
     * cross-implementation diff, not a divergence -- and a shared blind spot is the class that took reading a rule
     * against its own output to find, not a diff.
     *
     * <p>
     * <b>Measured, not assumed:</b> a sweep of all 200 validation-corpus documents -- wild, hold-out and local -- for
     * U+200B, U+200C, U+200D, U+2060, U+FEFF and U+00AD, raw and JSON-escaped, found <b>zero</b> occurrences. That
     * removes the urgency and answers nothing: a corpus is not production, and a zero-width character arrives by
     * copy-paste out of a browser or a word processor. Whether the ratified rule should STRIP such characters or REJECT
     * the document carrying them is open either way, and both choices re-key rows, which makes it a person's ruling and
     * a rule-set bump rather than either implementation's call. This test records the current answer and should change
     * only when that ruling exists.
     */
    @Test
    void aZeroWidthSpaceSurvivesInBothImplementations() {
        String zeroWidthSpace = Character.toString(0x200B);

        assertThat(AsciiText.strip("RSA" + zeroWidthSpace)).isEqualTo("RSA" + zeroWidthSpace);
        assertThat(AsciiText.isBlank(zeroWidthSpace)).isFalse();
    }

    @Test
    void runsOfWhitespaceCollapseToOneSpace() {
        assertThat(AsciiText.collapseWhitespace("a \t\n b")).isEqualTo("a b");
    }

    /**
     * Every text primitive treats an absent value as absent rather than throwing.
     *
     * <p>
     * A reducer reads a slot straight out of a producer's document, so every one of these is called with {@code null}
     * on ordinary input. A guard that is only exercised through a caller is a guard that moves when the caller does.
     */
    @Test
    void everyTextPrimitivePassesAbsenceThrough() {
        assertThat(AsciiText.fold(null)).isNull();
        assertThat(AsciiText.upper(null)).isNull();
        assertThat(AsciiText.lookupKey(null)).isNull();
        assertThat(AsciiText.strip(null)).isNull();
        assertThat(AsciiText.collapseWhitespace(null)).isNull();
        assertThat(AsciiText.isBlank(null)).isTrue();
    }

    /** The printable test is the ASCII range exactly: the control characters below it and everything above it fail. */
    @Test
    void printabilityIsTheAsciiRangeAndNothingElse() {
        assertThat(AsciiText.isAsciiPrintable("AES-256/GCM")).isTrue();
        assertThat(AsciiText.isAsciiPrintable("")).isTrue();
        assertThat(AsciiText.isAsciiPrintable("a\tb")).isFalse();
        assertThat(AsciiText.isAsciiPrintable("a" + Character.toString(0x7F) + "b")).isFalse();
        assertThat(AsciiText.isAsciiPrintable("caf\u00e9")).isFalse();
    }

    /**
     * The OID shape is digit groups separated by single dots, and nothing else.
     *
     * <p>
     * The grammar is scanned rather than matched because {@code \d+(\.\d+)*} recurses inside Java's matcher and
     * overflows the stack on a long enough input. These cases pin the grammar the scan implements, so a later move back
     * to a regex has something to answer to.
     */
    @Test
    void theDottedDigitShapeIsDigitGroupsSeparatedBySingleDots() {
        assertThat(AsciiText.isDottedDigits("1.2.840.113549", 3)).isTrue();
        assertThat(AsciiText.isDottedDigits("1.2", 1)).isTrue();

        assertThat(AsciiText.isDottedDigits("1.2", 3)).describedAs("too few arcs").isFalse();
        assertThat(AsciiText.isDottedDigits("1..2", 1)).describedAs("empty arc").isFalse();
        assertThat(AsciiText.isDottedDigits(".1.2", 1)).describedAs("leading dot").isFalse();
        assertThat(AsciiText.isDottedDigits("1.2.", 1)).describedAs("trailing dot").isFalse();
        assertThat(AsciiText.isDottedDigits("1.2a", 1)).describedAs("non-digit").isFalse();
        assertThat(AsciiText.isDottedDigits("", 0)).isFalse();
        assertThat(AsciiText.isDottedDigits(null, 0)).isFalse();
    }

    /** A digit is an ASCII digit, exactly as {@code \d} is by default -- so no keyed value moves with the locale. */
    @Test
    void anArabicIndicDigitIsNotADigitHere() {
        assertThat(AsciiText.isDottedDigits("1." + Character.toString(0x0661), 1)).isFalse();
    }

    // ---------------------------------------------------------------- slot escaping

    /**
     * A producer-controlled value cannot forge a slot boundary and impersonate another tier.
     *
     * <p>
     * A material id of {@code abc|F|x} otherwise renders {@code MAT|secret-key|I|abc|F|x}, which is exactly the shape a
     * fingerprint-tier asset emits.
     */
    @Test
    void aCraftedValueCannotForgeATierBoundary() {
        assertThat(PreImageSlot.of("abc|F|x")).isEqualTo("abc%7CF%7Cx");
    }

    @ParameterizedTest
    @CsvSource({"'a b', 'a%20b'", "'100%', '100%25'"})
    void theDelimiterSetIsEscaped(String input, String expected) {
        assertThat(PreImageSlot.of(input)).isEqualTo(expected);
    }

    @Test
    void controlDelimitersAreEscapedToo() {
        assertThat(PreImageSlot.of("a\tb")).isEqualTo("a%09b");
        assertThat(PreImageSlot.of("a\rb")).isEqualTo("a%0Db");
        assertThat(PreImageSlot.of("a\nb")).isEqualTo("a%0Ab");
    }

    /** Escaping is lossless, so no two distinct values are ever conflated by it. */
    @Test
    void escapingNeverConflatesTwoDistinctValues() {
        assertThat(PreImageSlot.of("a|b")).isNotEqualTo(PreImageSlot.of("a%7Cb"));
    }

    /** An absent value renders as the empty slot, which is distinct from every present value. */
    @Test
    void anAbsentValueIsDistinctFromEveryPresentOne() {
        assertThat(PreImageSlot.of((String) null)).isEmpty();
        assertThat(PreImageSlot.of("")).isEmpty();
        assertThat(PreImageSlot.of((Integer) null)).isEmpty();
        assertThat(PreImageSlot.of(Integer.valueOf(2048))).isEqualTo("2048");
    }

    // ---------------------------------------------------------------- timestamps

    /**
     * A spelling must not split a certificate, and parsing is case-insensitive because the reference's is.
     *
     * <p>
     * RFC 3339 permits a lowercase {@code t} separator and {@code z} zone and producers emit both. A case-sensitive
     * Java formatter left that spelling unparsed, keying the certificate on its <em>spelling</em> rather than on its
     * instant, and splitting it from every producer writing uppercase.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "2025-01-01T00:00:00Z",
            "2025-01-01T00:00:00.000Z",
            "2025-01-01t00:00:00z",
            "20250101000000Z",
            "2025-01-01T00:00:00.123456789Z"})
    void everySpellingOfOneInstantFoldsToOneValue(String spelling) {
        assertThat(ValidityTimestamps.normalize(spelling)).isEqualTo("1735689600");
    }

    @Test
    void anOffsetIsResolvedRatherThanKept() {
        assertThat(ValidityTimestamps.normalize("2025-01-01T02:00:00+0200")).isEqualTo("1735689600");
    }

    /**
     * An unparseable value is returned cleaned rather than discarded: it is still a fact the producer stated, and two
     * certificates differing only there must not merge.
     */
    @Test
    void anUnparseableTimestampIsKeptRatherThanDropped() {
        assertThat(ValidityTimestamps.normalize("not-a-date")).isEqualTo("not-a-date");
        assertThat(ValidityTimestamps.normalize("")).isEmpty();
        assertThat(ValidityTimestamps.normalize(null)).isEmpty();
    }

    @Test
    void anInstantIsAvailableWhereOneIsWantedRatherThanAKeyedString() {
        assertThat(ValidityTimestamps.instant("2025-01-01T00:00:00Z").getEpochSecond()).isEqualTo(1735689600L);
        assertThat(ValidityTimestamps.instant("not-a-date")).isNull();
        assertThat(ValidityTimestamps.instant(null)).isNull();
    }

    // ---------------------------------------------------------------- component names

    /**
     * A producer-generated identifier must not enter identity, or every row is rewritten on every scan.
     *
     * <p>
     * One producer names every secret-key asset {@code key@<random UUIDv4>}; all 33 reduce to one token, which is
     * exactly the desired behaviour.
     */
    @Test
    void generatedIdentifiersContributeNothing() {
        assertThat(ComponentNames.stableToken("key@ff11be02-d1ac-4887-9c11-000000000000"))
                .isEqualTo(ComponentNames.stableToken("key@0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9"));
    }

    /** Thirty-two, not sixteen: a 16-hex-digit run is a plausible meaningful identifier. */
    @Test
    void aShortHexRunIsMeaningfulAndSurvives() {
        assertThat(ComponentNames.stableToken("key-0123456789abcdef"))
                .isNotEqualTo(ComponentNames.stableToken("key-fedcba9876543210"));
    }

    /** A separator survives only between two digits, the one place it carries meaning. */
    @Test
    void punctuationIsDroppedExceptBetweenDigits() {
        assertThat(ComponentNames.stableToken("key-12")).isNotEqualTo(ComponentNames.stableToken("key1-2"));
        assertThat(ComponentNames.stableToken("PrivateKey"))
                .isEqualTo(ComponentNames.stableToken("Private-Key"))
                .isEqualTo(ComponentNames.stableToken("Private_Key"));
    }

    @Test
    void anEmptyNameYieldsAnEmptyToken() {
        assertThat(ComponentNames.stableToken(null)).isEmpty();
        assertThat(ComponentNames.stableToken("   ")).isEmpty();
    }

    /** Address-shaped runs go before any digit is read as a size, IPv4 before port so host:port loses both halves. */
    @Test
    void addressShapedRunsAreRemovedBeforeAnyDigitIsRead() {
        assertThat(ComponentNames.stripOpaqueTokens("cert 192.168.56.10:636"))
                .doesNotContain("192")
                .doesNotContain("636");
        assertThat(ComponentNames.stripOpaqueTokens("protocol:tls:localhost:13443")).doesNotContain("13443");
    }

    // ---------------------------------------------------------------- occurrence locations

    /**
     * A location feeds the identity key for version-less protocols and identity-less material, so a credential in one
     * would be hashed into the key and stored in the evidence payload.
     */
    @Test
    void credentialsAndVolatilePartsNeverReachAKey() {
        assertThat(sanitize("tcp://user:pass@host:443/path")).isEqualTo("tcp://host:443/path");
        assertThat(sanitize("https://host/path?token=secret")).isEqualTo("https://host/path");
        assertThat(sanitize("file:///a/b#fragment")).isEqualTo("file:///a/b");
    }

    @Test
    void anAbsentLocationIsTheEmptyString() {
        assertThat(Occurrences.sanitizeLocation(null)).isEmpty();
        assertThat(sanitize("   ")).isEmpty();
        assertThat(Occurrences.sanitizeLocation(new IntNode(7)))
                .describedAs("a location that is not a string states no location")
                .isEmpty();
    }

    /**
     * The cap may not cut between the halves of a surrogate pair.
     *
     * <p>
     * The cap counts UTF-16 units, so 1023 basic-plane characters followed by one astral character left a lone high
     * surrogate as the last char. {@link IdentityDigests#sha256Hex} refuses that, so the component became a reported
     * skip and vanished from the inventory; the same string also has no valid UTF-8 encoding for the jsonb evidence
     * column. The pair is dropped whole instead.
     */
    @Test
    void theLengthCapNeverSplitsASurrogatePair() {
        String astral = Character.toString(0x1F5DD);

        assertThat(sanitize("a".repeat(1023) + astral))
                .describedAs("the pair starts at unit 1023 and cannot fit, so neither half is kept")
                .isEqualTo("a".repeat(1023));
        assertThat(sanitize("a".repeat(1022) + astral)).isEqualTo("a".repeat(1022) + astral);
        assertThat(sanitize("a".repeat(2000))).hasSize(1024);
    }

    // ---------------------------------------------------------------- occurrence discriminator

    /**
     * Location alone under-discriminates, so the triple carries line and offset too.
     *
     * <p>
     * Measured on one producer's scan, 33 distinct secret keys occupied only 21 distinct location sets -- one source
     * file held five of them -- so a location-only discriminator silently merged twelve different secrets.
     */
    @Test
    void twoSecretsInOneFileAreNotMerged() {
        String first = Occurrences.triples(occurrences("[{\"location\": \"a.py\", \"line\": 1, \"offset\": 0}]"));
        String second = Occurrences.triples(occurrences("[{\"location\": \"a.py\", \"line\": 9, \"offset\": 0}]"));

        assertThat(first).isNotEqualTo(second);
    }

    /** The hashed string is exposed by name because a digest tells an implementer nothing about why it differs. */
    @Test
    void theDiscriminatorIsTheDigestOfTheExposedString() {
        JsonNode component = occurrences("[{\"location\": \"a.py\", \"line\": 1, \"offset\": 2}]");

        assertThat(Occurrences.triples(component)).isEqualTo("a.py#1#2");
        assertThat(Occurrences.discriminator(component)).isEqualTo(IdentityDigests.sha256Hex("a.py#1#2"));
    }

    /** Triples are sorted and de-duplicated, so a producer's array order cannot re-key an asset between scans. */
    @Test
    void arrayOrderAndRepetitionDoNotReachTheKey() {
        String ascending = Occurrences
                .triples(occurrences("[{\"location\": \"a.py\", \"line\": 1}, {\"location\": \"b.py\", \"line\": 2}]"));
        String descendingWithARepeat = Occurrences
                .triples(occurrences("[{\"location\": \"b.py\", \"line\": 2}, {\"location\": \"a.py\", \"line\": 1},"
                        + " {\"location\": \"b.py\", \"line\": 2}]"));

        assertThat(ascending).isEqualTo("a.py#1#\nb.py#2#").isEqualTo(descendingWithARepeat);
    }

    /**
     * An integral line renders through its exact integer value, never through a float's rendering of it.
     *
     * <p>
     * {@code JsonNode.asText()} on a large integral node can yield exponent notation, which would key the same line two
     * ways depending on how the producer serialized it. A non-integral line has no exact integer to render, so it keeps
     * the producer's own spelling rather than being rounded into one.
     */
    @Test
    void anIntegralLineRendersThroughItsExactValue() {
        assertThat(Occurrences.triples(occurrences("[{\"location\": \"a\", \"line\": 10000000000}]")))
                .isEqualTo("a#10000000000#");
        assertThat(Occurrences.triples(occurrences("[{\"location\": \"a\", \"line\": 1.5}]")))
                .describedAs("no exact integer exists, so the producer's spelling stands")
                .isEqualTo("a#1.5#");
    }

    /**
     * A producer-controlled line cannot forge a triple boundary.
     *
     * <p>
     * Triples are joined with a newline, so a line of {@code "1\nb#9#9"} would otherwise render as two triples and
     * claim an occurrence the producer never reported. The newline is escaped in the slot; {@code #} is left literal
     * because it separates fields <em>within</em> one triple, which the producer already controls all three of.
     */
    @Test
    void aCraftedLineCannotForgeATripleBoundary() {
        assertThat(Occurrences.triples(occurrences("[{\"location\": \"a\", \"line\": \"1\\nb\"}]")))
                .isEqualTo("a#1%0Ab#");
    }

    /** No occurrences at all is absence, not an empty digest -- an assetless discriminator must not key anything. */
    @Test
    void aComponentWithNoOccurrencesHasNoDiscriminator() {
        assertThat(Occurrences.triples(null)).isNull();
        assertThat(Occurrences.discriminator(null)).isNull();
        assertThat(Occurrences.triples(read("{}"))).isNull();
        assertThat(Occurrences.triples(read("{\"evidence\": {}}"))).isNull();
        assertThat(Occurrences.triples(read("{\"evidence\": {\"occurrences\": \"a.py\"}}"))).isNull();
        assertThat(Occurrences.triples(occurrences("[]"))).isNull();
        assertThat(Occurrences.triples(occurrences("[\"a.py\", 7]")))
                .describedAs("entries that are not objects carry no triple at all")
                .isNull();
    }

    private static String sanitize(String location) {
        return Occurrences.sanitizeLocation(new TextNode(location));
    }

    private static JsonNode occurrences(String occurrencesArrayJson) {
        return read("{\"evidence\": {\"occurrences\": " + occurrencesArrayJson + "}}");
    }

    private static JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(json, e);
        }
    }
}
