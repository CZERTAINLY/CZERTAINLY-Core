package com.otilm.core.cbom.asset.identity;

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
        assertThat(KeySlot.of("abc|F|x")).isEqualTo("abc%7CF%7Cx");
    }

    @ParameterizedTest
    @CsvSource({"'a b', 'a%20b'", "'100%', '100%25'"})
    void theDelimiterSetIsEscaped(String input, String expected) {
        assertThat(KeySlot.of(input)).isEqualTo(expected);
    }

    @Test
    void controlDelimitersAreEscapedToo() {
        assertThat(KeySlot.of("a\tb")).isEqualTo("a%09b");
        assertThat(KeySlot.of("a\rb")).isEqualTo("a%0Db");
        assertThat(KeySlot.of("a\nb")).isEqualTo("a%0Ab");
    }

    /** Escaping is lossless, so no two distinct values are ever conflated by it. */
    @Test
    void escapingNeverConflatesTwoDistinctValues() {
        assertThat(KeySlot.of("a|b")).isNotEqualTo(KeySlot.of("a%7Cb"));
    }

    /** An absent value renders as the empty slot, which is distinct from every present value. */
    @Test
    void anAbsentValueIsDistinctFromEveryPresentOne() {
        assertThat(KeySlot.of((String) null)).isEmpty();
        assertThat(KeySlot.of("")).isEmpty();
        assertThat(KeySlot.of((Integer) null)).isEmpty();
        assertThat(KeySlot.of(Integer.valueOf(2048))).isEqualTo("2048");
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
        assertThat(Timestamps.normalize(spelling)).isEqualTo("1735689600");
    }

    @Test
    void anOffsetIsResolvedRatherThanKept() {
        assertThat(Timestamps.normalize("2025-01-01T02:00:00+0200")).isEqualTo("1735689600");
    }

    /**
     * An unparseable value is returned cleaned rather than discarded: it is still a fact the producer stated, and two
     * certificates differing only there must not merge.
     */
    @Test
    void anUnparseableTimestampIsKeptRatherThanDropped() {
        assertThat(Timestamps.normalize("not-a-date")).isEqualTo("not-a-date");
        assertThat(Timestamps.normalize("")).isEmpty();
        assertThat(Timestamps.normalize(null)).isEmpty();
    }

    @Test
    void anInstantIsAvailableWhereOneIsWantedRatherThanAKeyedString() {
        assertThat(Timestamps.instant("2025-01-01T00:00:00Z").getEpochSecond()).isEqualTo(1735689600L);
        assertThat(Timestamps.instant("not-a-date")).isNull();
        assertThat(Timestamps.instant(null)).isNull();
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
    }

    private static String sanitize(String location) {
        return Occurrences.sanitizeLocation(new TextNode(location));
    }
}
