package com.otilm.core.serialization.golden;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Compares serialized JSON against a committed golden file. Regenerate deliberately with
 * {@code -Dgolden.regenerate=true} and review the diff — see the package javadoc.
 */
final class GoldenJson {

    private static final String REGENERATE_PROPERTY = "golden.regenerate";

    private static final String RESOURCE_DIR = "golden";

    private static final Path SOURCE_DIR = Path.of("src", "test", "resources", RESOURCE_DIR);

    private GoldenJson() {
    }

    /** @param goldenName file name under {@code src/test/resources/golden}, without the {@code .json} suffix */
    static void assertMatchesGolden(String goldenName, ObjectMapper mapper, Object value) {
        String actual = serialize(mapper, value);

        if (regenerating()) {
            write(goldenName, actual);
            return;
        }

        String expected = read(goldenName);
        assertThat(actual).describedAs(driftMessage(goldenName)).isEqualTo(expected);
    }

    /**
     * Also asserts the value survives a deserialize/re-serialize cycle unchanged, which catches a type whose write and
     * read sides disagree — for a column, a row that mutates on every load-and-save.
     *
     * @param readAs the declared type production reads the payload back as, which decides how polymorphic type
     * information is resolved
     */
    static void assertMatchesGoldenAndRoundTrips(String goldenName, ObjectMapper mapper, Object value,
            Class<?> readAs) {
        assertMatchesGolden(goldenName, mapper, value);

        String serialized = serialize(mapper, value);
        Object reread;
        try {
            reread = mapper.readValue(serialized, readAs);
        } catch (IOException e) {
            throw new UncheckedIOException("Golden '" + goldenName + "' could not be read back as " + readAs.getName(),
                    e);
        }

        assertThat(serialize(mapper, reread))
                .describedAs("Round-tripping golden '%s.json' through %s changed its JSON shape: its write and read "
                        + "sides disagree.", goldenName, readAs.getSimpleName())
                .isEqualTo(serialized);
    }

    /**
     * For JSON produced by something other than an {@link ObjectMapper} (Hibernate's {@code FormatMapper} returns a
     * compact string), re-indented with the same pinned printer. Floats are read as {@code BigDecimal} so a literal
     * like {@code 1.5} survives the reformat; only whitespace is normalized.
     */
    static void assertRawJsonMatchesGolden(String goldenName, String rawJson) {
        String actual = reindent(rawJson);

        if (regenerating()) {
            write(goldenName, actual);
            return;
        }

        assertThat(actual).describedAs(driftMessage(goldenName)).isEqualTo(read(goldenName));
    }

    private static String driftMessage(String goldenName) {
        return "Serialized JSON drifted from golden '" + goldenName + ".json'. During the Jackson 3 migration this is "
                + "a finding to explain, not a test to update: trace the diff to a documented behaviour change before "
                + "regenerating with -D" + REGENERATE_PROPERTY + "=true.";
    }

    private static String reindent(String rawJson) {
        ObjectMapper reader = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        try {
            return serialize(reader, reader.readTree(rawJson));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not re-indent JSON for golden comparison", e);
        }
    }

    /**
     * Pins the printer to {@code \n} and two-space indents; the platform default uses the system line separator, which
     * would fail goldens on another OS for no behavioural reason.
     */
    private static String serialize(ObjectMapper mapper, Object value) {
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);

        ObjectWriter writer = mapper.writer(printer);
        try {
            return writer.writeValueAsString(value);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not serialize golden candidate", e);
        }
    }

    private static boolean regenerating() {
        if (!Boolean.getBoolean(REGENERATE_PROPERTY)) {
            return false;
        }
        // Regeneration asserts nothing, so on CI it would turn the baseline into a no-op that looks like success.
        if (System.getenv("CI") != null) {
            throw new IllegalStateException("-D" + REGENERATE_PROPERTY + "=true must never be used on CI: it would "
                    + "rewrite the goldens instead of checking them, and the suite would pass while verifying nothing.");
        }
        return true;
    }

    private static String read(String goldenName) {
        Path onDisk = SOURCE_DIR.resolve(goldenName + ".json");
        try {
            if (Files.exists(onDisk)) {
                return normalize(Files.readString(onDisk, StandardCharsets.UTF_8));
            }
            // Fall back to the classpath copy when the working directory is not the module root.
            try (InputStream stream = GoldenJson.class
                    .getResourceAsStream("/" + RESOURCE_DIR + "/" + goldenName + ".json")) {
                if (stream == null) {
                    return fail("Golden '%s.json' does not exist. Create it by running with -D%s=true and reviewing "
                            + "the generated file before committing.", goldenName, REGENERATE_PROPERTY);
                }
                return normalize(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read golden '" + goldenName + ".json'", e);
        }
    }

    private static void write(String goldenName, String content) {
        Path target = SOURCE_DIR.resolve(goldenName + ".json");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write golden '" + goldenName + ".json'", e);
        }
    }

    /** Tolerate the trailing newline every sane editor adds and CRLF from a Windows checkout. */
    private static String normalize(String content) {
        return content.replace("\r\n", "\n").stripTrailing();
    }
}
