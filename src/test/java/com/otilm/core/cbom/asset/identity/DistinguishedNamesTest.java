package com.otilm.core.cbom.asset.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the distinguished-name form the certificate tiers key on. */
class DistinguishedNamesTest {

    private static final IdentityTables TABLES = IdentityTables.load();

    /**
     * Attribute types become dotted OIDs, never short names, and the long spellings map too.
     *
     * <p>
     * Both implementations of this specification once carried an abbreviation-only map, so a producer writing
     * {@code commonName=} got a lower-cased short name in the slot where the rule says a dotted OID always goes.
     * Because both sides reproduced the wart together, no cross-implementation agreement measurement could ever have
     * found it: it took reading the rule against its own output. The mapping is read from the ratified tables for that
     * reason.
     */
    @ParameterizedTest
    @CsvSource({
            "'CN=x', '2.5.4.3=x'",
            "'commonName=x', '2.5.4.3=x'",
            "'O=x', '2.5.4.10=x'",
            "'organizationName=x', '2.5.4.10=x'",
            "'DC=x', '0.9.2342.19200300.100.1.25=x'",
            "'domainComponent=x', '0.9.2342.19200300.100.1.25=x'",
            "'UID=x', '0.9.2342.19200300.100.1.1=x'"})
    void attributeTypesBecomeDottedOids(String input, String expected) {
        assertThat(DistinguishedNames.normalize(input, TABLES)).isEqualTo(expected);
    }

    /**
     * RDNs sort on the rendered {@code oid=value} string, so {@code 2.5.4.10} precedes {@code 2.5.4.3}.
     *
     * <p>
     * Not on the value, which the specification's own prose claimed for months. Sorting at all is an availability fact
     * rather than a preference: the producer's renderer has already rebuilt the RDNs in a hardcoded field order and
     * discarded true DER order, so preserving source order would make one certificate reported by two producers never
     * agree.
     */
    @Test
    void rdnsSortOnTheRenderedStringNotOnTheValue() {
        assertThat(DistinguishedNames.normalize("CN=zebra,O=alpha", TABLES)).isEqualTo("2.5.4.10=alpha,2.5.4.3=zebra");
        assertThat(DistinguishedNames.normalize("O=alpha,CN=zebra", TABLES))
                .describedAs("source order must not survive")
                .isEqualTo("2.5.4.10=alpha,2.5.4.3=zebra");
    }

    /**
     * A value carrying no {@code =} at all is a bare common name, not a malformed DN.
     *
     * <p>
     * Refusing it made the composite unconstructible and dropped two <em>different</em> root CAs -- same subject,
     * different validity windows -- onto one identity.
     */
    @Test
    void aBareNameBecomesACommonName() {
        assertThat(DistinguishedNames.normalize("EJBCA-Root-CA", TABLES)).isEqualTo("2.5.4.3=ejbca-root-ca");
    }

    /** A plain split would break a real corpus value into two RDNs. */
    @Test
    void anEscapedSeparatorDoesNotSplitAnRdn() {
        assertThat(DistinguishedNames.normalize("O=Qualys\\, Inc.", TABLES)).isEqualTo("2.5.4.10=qualys\\, inc.");
    }

    /** Folding is ASCII-only and applies only where the attribute's syntax allows it. Default-deny. */
    @Test
    void onlyCaseInsensitiveAttributesFold() {
        assertThat(DistinguishedNames.normalize("CN=MixedCase", TABLES)).isEqualTo("2.5.4.3=mixedcase");
        assertThat(DistinguishedNames.normalize("serialNumber=ABCdef", TABLES))
                .describedAs("a serial number is compared verbatim")
                .isEqualTo("2.5.4.5=ABCdef");
        assertThat(DistinguishedNames.normalize("emailAddress=Foo@Bar.com", TABLES))
                .describedAs("RFC 5321 local-parts are case-sensitive")
                .isEqualTo("1.2.840.113549.1.9.1=Foo@Bar.com");
    }

    /**
     * The pipe is escaped because the identity pre-image is pipe-delimited: an unescaped one inside a crafted common
     * name could shift every later field boundary and forge a collision with a different tier.
     */
    @Test
    void theDelimiterCannotBeSmuggledThroughAValue() {
        assertThat(DistinguishedNames.normalize("CN=a|b", TABLES)).isEqualTo("2.5.4.3=a\\|b");
    }

    /** Whitespace collapses and NFKC applies, so a fullwidth or padded spelling cannot split a row. */
    @Test
    void spacingAndCompatibilitySpellingsDoNotSplitARow() {
        assertThat(DistinguishedNames.normalize("cn=  Spaced   Out  ", TABLES)).isEqualTo("2.5.4.3=spaced out");
        assertThat(DistinguishedNames.normalize("CN=ＲＳＡ", TABLES)).isEqualTo("2.5.4.3=rsa");
    }

    /**
     * A trailing no-break space is stripped, which {@link String#strip()} would not do.
     *
     * <p>
     * NFKC turns a surviving one into an ordinary trailing space, keying {@code "x "} apart from {@code "x"}.
     */
    @Test
    void aTrailingNoBreakSpaceIsStripped() {
        assertThat(DistinguishedNames.normalize("CN=x ", TABLES)).isEqualTo("2.5.4.3=x");
        assertThat(DistinguishedNames.normalize("CN=x ", TABLES)).isEqualTo("2.5.4.3=x");
    }

    /**
     * Go emits every attribute type it does not know this way, and decoding is mandatory or two producers never match.
     */
    @Test
    void theHexDerFormIsDecodedBeforeComparison() {
        assertThat(DistinguishedNames.normalize("CN=#414243", TABLES)).isEqualTo("2.5.4.3=abc");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void anEmptyInputYieldsNoName(String input) {
        assertThat(DistinguishedNames.normalize(input, TABLES)).isNull();
    }

    /**
     * A malformed attribute is kept as itself rather than discarded, which is what the reference does.
     *
     * <p>
     * Keeping it is the safer direction: an attribute nobody can parse is still a fact the producer stated, and two
     * certificates differing only there must not merge. Verified against the reference rather than assumed -- the first
     * draft of this test asserted these were dropped, and the reference disagreed.
     */
    @ParameterizedTest
    @CsvSource({"'=', '='", "'a=', 'a='", "'=b', '=b'"})
    void anUnparseableAttributeSurvivesAsItself(String input, String expected) {
        assertThat(DistinguishedNames.normalize(input, TABLES)).isEqualTo(expected);
    }

    @Test
    void aNullInputYieldsNoName() {
        assertThat(DistinguishedNames.normalize(null, TABLES)).isNull();
    }

    /**
     * A CN-only observation is never merged into a full-DN row.
     *
     * <p>
     * {@code CN=localhost} is issued endlessly by internal CAs. Merging would fuse two certificates into one row that
     * inherits one certificate's key size and expiry and both certificates' occurrences, so an operator asking "where
     * is weak crypto deployed" gets CLEAN for a vulnerable host.
     */
    @Test
    void aCommonNameOnlySubjectIsRecognized() {
        assertThat(DistinguishedNames.isCommonNameOnly(DistinguishedNames.normalize("CN=localhost", TABLES))).isTrue();
        assertThat(DistinguishedNames.isCommonNameOnly(DistinguishedNames.normalize("CN=x,O=y", TABLES))).isFalse();
        assertThat(DistinguishedNames.isCommonNameOnly(null)).isFalse();
    }

    /** Multi-valued RDNs sort within themselves, so a producer's ordering of a {@code +} pair cannot split a row. */
    @Test
    void multiValuedRdnsSortWithinThemselves() {
        assertThat(DistinguishedNames.normalize("CN=b+O=a", TABLES))
                .isEqualTo(DistinguishedNames.normalize("O=a+CN=b", TABLES));
    }
}
