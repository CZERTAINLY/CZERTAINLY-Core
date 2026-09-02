package com.otilm.core.cbom.asset.identity;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Canonical, unambiguous, hashable form of a distinguished name.
 *
 * <p>
 * Core already persists a normalized DN via {@code PlatformX500NameStyle.NORMALIZED}, and it is already OID-typed and
 * sorted. Three gaps make it unsafe to hash as-is: no case folding, no Unicode or whitespace normalization, and no
 * escaping -- a raw comma inside a value ({@code 2.5.4.10=Org, s.r.o.}) makes the serialization ambiguous, so two
 * different DNs can produce one pre-image. This closes all three.
 *
 * <p>
 * The rules, each of which is load-bearing rather than tidy:
 *
 * <ul>
 * <li><b>Attribute types become dotted OIDs</b>, never short names. Core's short names are runtime-mutable
 * ({@code OidHandler.cacheOid}, and {@code updateCertificateSubjectDN} exists precisely to rewrite DNs on an OID
 * rename), so a short-name key would orphan every stored row on a rename. The mapping is read from the ratified tables,
 * which carry the long spellings too.</li>
 * <li><b>RDNs sort on the full rendered {@code oid=value} string</b>, plain lexicographic -- so {@code 2.5.4.10}
 * precedes {@code 2.5.4.3}, and it is not OID-numeric order either. Not on the normalized value, which the
 * specification claimed for months and which was caught by diffing vectors against it. Sorting is not a preference: the
 * producer's own renderer has already rebuilt the RDNs in a hardcoded field order and discarded true DER order, so
 * preserving source order would make one certificate reported by two producers never agree.</li>
 * <li><b>{@code OID=#hexDER} is decoded</b> before comparison, because that is what one producer emits for every
 * attribute type it does not know.</li>
 * <li><b>A DN carrying no {@code =} at all is a bare common name</b>, not a malformed DN. Refusing it made the
 * composite unconstructible and dropped two <em>different</em> root CAs onto one identity.</li>
 * </ul>
 */
public final class DistinguishedNames {

    /**
     * Attribute types whose values are case-insensitive by their syntax. Default-deny: anything not listed is compared
     * verbatim. {@code serialNumber}, {@code emailAddress} (RFC 5321 local-parts are case-sensitive),
     * {@code dnQualifier} and binary attributes are excluded deliberately.
     */
    private static final String COMMON_NAME_OID = "2.5.4.3";

    private static final Set<String> FOLDABLE_ATTRIBUTE_OIDS = Set
            .of(COMMON_NAME_OID, "2.5.4.4", "2.5.4.6", "2.5.4.7", "2.5.4.8", "2.5.4.9", "2.5.4.10", "2.5.4.11",
                    "2.5.4.12", "2.5.4.15", "2.5.4.17", "2.5.4.41", "2.5.4.65", "2.5.4.97",
                    "0.9.2342.19200300.100.1.25", "0.9.2342.19200300.100.1.1");

    private DistinguishedNames() {
    }

    /** The canonical form, or {@code null} when the input carries no distinguished name at all. */
    public static String normalize(String dn, IdentityTables tables) {
        if (dn == null || AsciiText.isBlank(dn)) {
            return null;
        }
        String trimmed = AsciiText.strip(dn);
        if (trimmed.indexOf('=') < 0) {
            String collapsed = AsciiText.collapseWhitespace(Normalizer.normalize(trimmed, Normalizer.Form.NFKC));
            return COMMON_NAME_OID + "=" + escape(AsciiText.fold(collapsed));
        }

        List<String> rdns = new ArrayList<>();
        for (String rdn : splitUnescaped(trimmed, ",")) {
            if (AsciiText.isBlank(rdn)) {
                continue;
            }
            List<String> avas = new ArrayList<>();
            for (String ava : splitUnescaped(rdn, "+")) {
                int separator = ava.indexOf('=');
                if (separator < 0) {
                    continue;
                }
                String rawType = AsciiText.fold(AsciiText.strip(ava.substring(0, separator)));
                String oid = tables.dnAttributeOids().getOrDefault(rawType, rawType);
                String value = normalizeValue(unescape(AsciiText.strip(ava.substring(separator + 1))), oid);
                avas.add(oid + "=" + escape(value));
            }
            if (!avas.isEmpty()) {
                avas.sort(null);
                rdns.add(String.join("+", avas));
            }
        }
        if (rdns.isEmpty()) {
            return null;
        }
        rdns.sort(null);
        return String.join(",", rdns);
    }

    /**
     * True when the DN carries nothing but a common name.
     *
     * <p>
     * A CN-only observation is deliberately not merged into a full-DN row. Merging them would fuse two certificates
     * that share a CN -- {@code CN=localhost} is issued endlessly by internal CAs -- into one row that inherits one
     * certificate's key size and expiry and both certificates' occurrences. In an inventory whose job is "where is weak
     * crypto deployed", that makes the operator's query return CLEAN for a vulnerable host. The asymmetry decides it: a
     * missed merge is one visible, repairable duplicate row; a wrong merge is silent corruption.
     */
    public static boolean isCommonNameOnly(String normalizedDn) {
        if (normalizedDn == null || normalizedDn.isEmpty()) {
            return false;
        }
        // Every RDN must be a single common-name AVA. Checking only the start of each one classified the multi-valued
        // `CN=x+SN=y` as CN-only whenever CN happened to sort first, so the reported step depended on attribute order.
        // `CN=a,CN=b` stays CN-only: it carries nothing but common names, which is what the name claims.
        return splitUnescaped(normalizedDn, ",")
                .stream()
                .allMatch(part -> part.startsWith(COMMON_NAME_OID + "=") && splitUnescaped(part, "+").size() == 1);
    }

    private static String normalizeValue(String raw, String oid) {
        // One escape rule, three paths, and they must not overlap. A bare `%XX` in the output means "a byte no
        // decoder could read"; every other percent is escaped to `%25` -- here for an ordinary textual value, and
        // inside decodeHexDer for a hex value that decodes. Escaping only inside the hex paths left `CN=#FF` and
        // `CN=%FF` rendering one AVA, because a literal value never enters that method.
        String value = raw.startsWith("#") ? decodeHexDer(raw) : raw.replace("%", "%25");
        value = Normalizer.normalize(value, Normalizer.Form.NFKC);
        value = AsciiText.strip(AsciiText.collapseWhitespace(value));
        // ASCII-only (R12). A value differing only in non-ASCII case keys separately; the case-fold twin detector
        // reports the pair rather than merging it, which is what makes the under-merge visible instead of silent.
        return FOLDABLE_ATTRIBUTE_OIDS.contains(oid) ? AsciiText.fold(value) : value;
    }

    /**
     * Decodes the {@code #hexDER} form one producer emits for every attribute type it does not know, refusing to
     * collapse bytes that are not UTF-8.
     *
     * <p>
     * <b>{@code new String(bytes, UTF_8)} was lossy in the identity-bearing direction.</b> It maps every malformed
     * sequence to U+FFFD, so {@code #1401E9}, {@code #1401EA} and {@code #1401FF} produced one byte-identical AVA and
     * two different issuers merged onto one row -- and {@code DocumentScope.certificateDigestClaims} normalizes the
     * same values, so the refutation index could not see the contradiction either. This is the decoding-side twin of
     * the unpaired-surrogate hole closed on the encoding side.
     *
     * <p>
     * A sequence that is not UTF-8 renders as its bytes, percent-escaped -- {@code %14%01%E9} -- which is ASCII and
     * reproducible in the reference kernel, whose {@code decode("utf-8", "replace")} carries the same defect and moves
     * with this. Escaping the whole value rather than the offending run keeps the two implementations from having to
     * agree on where a malformed run begins.
     *
     * <p>
     * <b>The escape namespace is reserved across every path, or the escape is not injective.</b> Two attempts got this
     * wrong before it was right. Escaping only the fallback moved the merge rather than closing it: {@code #FF} is not
     * UTF-8 and renders {@code %FF}, while {@code #254646} decodes cleanly to the three ASCII characters {@code %FF}.
     * Escaping both hex paths closed that pair and left a third: an ordinary textual AVA of {@code %FF} never enters
     * this method at all, so {@code CN=#FF} and {@code CN=%FF} still rendered one value. The rule that holds is that
     * <em>any</em> {@code %} in a normalized attribute value came from an escape here -- so {@link #normalizeValue}
     * escapes the literal path instead of this method escaping it twice, and a bare {@code %XX} can only mean a byte no
     * decoder could read. 0 of 1 595 corpus DN values carry a {@code %} or a {@code #} at all, so nothing moves today;
     * what changes is that a forged value no longer merges.
     */
    private static String decodeHexDer(String value) {
        try {
            byte[] decoded = HexFormat.of().parseHex(value.substring(1));
            String text = strictUtf8(decoded);
            StringBuilder printable = new StringBuilder(text.length());
            text.codePoints().filter(DistinguishedNames::isPrintable).forEach(printable::appendCodePoint);
            return AsciiText.strip(printable.toString());
        } catch (IllegalArgumentException e) {
            // Not hex after all. The leading marker is dropped and the rest compared verbatim, which is what the
            // reference does -- refusing the value outright would lose a real attribute.
            return value.substring(1);
        }
    }

    private static String strictUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
                    // Escaped here and not by the caller: the fallback below must keep its escapes bare, or a
                    // malformed byte and a decoded value spelling that byte's escape render alike -- which is item
                    // 17's own merge, moved rather than closed.
                    .replace("%", "%25");
        } catch (CharacterCodingException e) {
            // Bare, and the only bare escapes in the result: the caller escapes the decode path's percents first, so
            // `%FF` from here and a decoded literal `%FF` cannot render alike.
            StringBuilder escaped = new StringBuilder(bytes.length * 3);
            for (byte value : bytes) {
                escaped.append('%').append(HexFormat.of().withUpperCase().toHexDigits(value));
            }
            return escaped.toString();
        }
    }

    /**
     * Mirrors the reference's printability test, which is Unicode's rather than ASCII's: a character is printable
     * unless it is a separator or an "other" category, with space itself the one exception.
     */
    private static boolean isPrintable(int codePoint) {
        if (codePoint == ' ') {
            return true;
        }
        return switch (Character.getType(codePoint)) {
            case Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR, Character.CONTROL,
                    Character.FORMAT, Character.SURROGATE, Character.PRIVATE_USE, Character.UNASSIGNED ->
                false;
            default -> true;
        };
    }

    /**
     * Splits on separators, honouring RFC 4514 backslash escapes.
     *
     * <p>
     * A plain split would break {@code O=Qualys\, Inc.} -- a real value in the corpus -- into two RDNs.
     */
    static List<String> splitUnescaped(String text, String separators) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (escaped) {
                current.append(character);
                escaped = false;
            } else if (character == '\\') {
                current.append(character);
                escaped = true;
            } else if (separators.indexOf(character) >= 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private static String unescape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (escaped) {
                out.append(character);
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else {
                out.append(character);
            }
        }
        return out.toString();
    }

    /**
     * Escapes the characters that would otherwise make a serialized DN ambiguous.
     *
     * <p>
     * The pipe is in the set on purpose: the identity pre-image is pipe-delimited, so an unescaped {@code |} inside a
     * crafted common name could shift every later field boundary and forge a collision.
     */
    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '\\', ',', '+', '=', '<', '>', ';', '"', '#', '|' -> out.append('\\').append(character);
                default -> out.append(character);
            }
        }
        return out.toString();
    }
}
