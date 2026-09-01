package com.otilm.core.cbom.asset.identity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Folds an ISO-8601 instant to epoch seconds, so a spelling cannot split a row.
 *
 * <p>
 * Producers write the same instant several ways: {@code 2025-01-01T00:00:00Z} and {@code 2025-01-01T00:00:00.000Z}
 * split one certificate across two rows when hashed verbatim. Sub-second precision is discarded deliberately -- X.509
 * validity has one-second granularity, so it carries no identity.
 *
 * <p>
 * The same normalization applies wherever validity is <em>compared</em> and not only where it is hashed. The
 * digest-refutation index once compared raw strings, so a millisecond suffix could refute a sound digest and push both
 * certificates down a tier.
 */
public final class ValidityTimestamps {

    private static final Pattern FRACTION = Pattern.compile("\\.\\d+");

    /**
     * Parsed case-insensitively, because the reference's parser is. RFC 3339 permits a lowercase {@code t} separator
     * and a lowercase {@code z} zone, and real producers emit both -- the reference folds only the {@code z} explicitly
     * and gets the {@code t} for free from a case-insensitive matcher. A case-sensitive Java formatter would leave
     * {@code 2025-01-01t00:00:00z} unparsed, keying it on its spelling instead of on its instant.
     */
    private static final List<DateTimeFormatter> OFFSET_FORMATS = List
            .of(caseInsensitiveOffset("+HH:MM"), caseInsensitive("yyyy-MM-dd'T'HH:mm:ssZ"));

    private static final List<DateTimeFormatter> LOCAL_FORMATS = List
            .of(caseInsensitive("yyyy-MM-dd'T'HH:mm:ss'Z'"), caseInsensitive("yyyyMMddHHmmss'Z'"));

    private static DateTimeFormatter caseInsensitive(String pattern) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter();
    }

    private static DateTimeFormatter caseInsensitiveOffset(String offsetPattern) {
        return new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
                .appendOffset(offsetPattern, "Z")
                .toFormatter();
    }

    private ValidityTimestamps() {
    }

    /**
     * Epoch seconds as a bare string, or the cleaned input when it parses as no known instant.
     *
     * <p>
     * An unparseable value is returned rather than discarded: it is still a fact the producer stated, and two
     * certificates differing only in an unparseable validity must not merge. The empty string means absent, which is
     * distinct from every present value.
     */
    public static String normalize(String raw) {
        if (raw == null || AsciiText.isBlank(raw)) {
            return "";
        }
        String cleaned = FRACTION.matcher(AsciiText.strip(raw)).replaceAll("").replace("z", "Z");
        for (DateTimeFormatter format : OFFSET_FORMATS) {
            try {
                return Long.toString(OffsetDateTime.parse(cleaned, format).toEpochSecond());
            } catch (DateTimeParseException e) {
                // Not this offset spelling. The local spellings are tried below, and a value matching none is returned
                // cleaned rather than dropped.
            }
        }
        for (DateTimeFormatter format : LOCAL_FORMATS) {
            try {
                return Long.toString(LocalDateTime.parse(cleaned, format).toInstant(ZoneOffset.UTC).getEpochSecond());
            } catch (DateTimeParseException e) {
                // As above.
            }
        }
        return cleaned;
    }

    /** Parses to an instant where one is wanted rather than a keyed string. Absent when the value is unparseable. */
    public static Instant instant(String raw) {
        String normalized = normalize(raw);
        try {
            return normalized.isEmpty() ? null : Instant.ofEpochSecond(Long.parseLong(normalized));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
