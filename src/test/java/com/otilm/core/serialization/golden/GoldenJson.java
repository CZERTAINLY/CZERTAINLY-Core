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
 * Compares serialized JSON against a committed golden file, so a change in Jackson's output shape fails a test instead
 * of silently reaching a client, a connector, or a persisted JSON column.
 * <p>
 * These goldens exist to baseline Jackson 2 behaviour ahead of the Spring Boot 4.1 / Jackson 3 migration
 * (OmniTrustILM/core#1941). During that migration a golden diff is a <b>migration finding to be explained</b>, not a
 * test to be updated: each diff must be traced to a documented Jackson 3 behaviour change and either accepted
 * deliberately or fixed, before the golden is regenerated.
 * <p>
 * To regenerate intentionally, run the suite with {@code -Dgolden.regenerate=true}; every golden touched by the run is
 * rewritten from current behaviour. Review the resulting diff in the PR — an unreviewed regeneration defeats the entire
 * purpose of the baseline.
 * <p>
 * This class deliberately uses no Spring machinery. The suite must stay outside the Spring test-context taxonomy so it
 * costs zero context boots and leaves {@code ContextSignatureGuardTest.BASELINE} untouched.
 */
final class GoldenJson {

    private static final String REGENERATE_PROPERTY = "golden.regenerate";

    private static final String RESOURCE_DIR = "golden";

    private static final Path SOURCE_DIR = Path.of("src", "test", "resources", RESOURCE_DIR);

    private GoldenJson() {
    }

    /**
     * Assert that {@code value} serializes through {@code mapper} to exactly the committed golden file.
     *
     * @param goldenName file name under {@code src/test/resources/golden}, without the {@code .json} suffix
     */
    static void assertMatchesGolden(String goldenName, ObjectMapper mapper, Object value) {
        String actual = serialize(mapper, value);

        if (regenerating()) {
            write(goldenName, actual);
            return;
        }

        String expected = read(goldenName);
        assertThat(actual)
                .describedAs("Serialized JSON drifted from golden '%s.json'. During the Jackson 3 migration this is a "
                        + "finding to explain, not a test to update — trace the diff to a documented behaviour change "
                        + "first. Regenerate deliberately with -D%s=true once the diff is understood.", goldenName,
                        REGENERATE_PROPERTY)
                .isEqualTo(expected);
    }

    /**
     * Assert the golden shape and that the value survives a deserialize/re-serialize cycle unchanged.
     * <p>
     * The re-serialization leg is what catches an <i>asymmetric</i> mapping — a type whose write side and read side
     * disagree, which for a JSON database column means a row silently changes shape every time it is loaded and saved.
     * A write-only golden would not notice.
     *
     * @param readAs the declared type the production code reads the column/payload back as, which is what decides how
     * polymorphic type information is resolved
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
                .describedAs("Round-tripping golden '%s.json' through %s changed its JSON shape: the write and read "
                        + "sides of this type disagree, so a stored value would mutate on every load-and-save cycle.",
                        goldenName, readAs.getSimpleName())
                .isEqualTo(serialized);
    }

    /**
     * Assert that a JSON document produced by something other than an {@link ObjectMapper} matches its golden.
     * <p>
     * Hibernate's {@code FormatMapper} returns a compact string rather than writing through an {@code ObjectWriter}, so
     * its output is re-indented here with the same pinned printer the mapper path uses. Only whitespace is normalized:
     * floats are read as {@code BigDecimal} so a literal like {@code 1.5} survives the reformat exactly, and key order,
     * key presence and scalar rendering — the things a migration would change — pass through untouched.
     */
    static void assertRawJsonMatchesGolden(String goldenName, String rawJson) {
        String actual = reindent(rawJson);

        if (regenerating()) {
            write(goldenName, actual);
            return;
        }

        assertThat(actual)
                .describedAs("Serialized JSON drifted from golden '%s.json'. During the Jackson 3 migration this is a "
                        + "finding to explain, not a test to update — trace the diff to a documented behaviour change "
                        + "first. Regenerate deliberately with -D%s=true once the diff is understood.", goldenName,
                        REGENERATE_PROPERTY)
                .isEqualTo(read(goldenName));
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
     * Serialize with a pretty printer pinned to {@code \n} and two-space indents. The platform-default printer uses the
     * system line separator, which would make goldens fail on a different OS for no behavioural reason. Whitespace is
     * normalized; key names, key order, presence and scalar rendering — the things that matter — are exactly as the
     * mapper produced them.
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
        // Regeneration rewrites goldens from current behaviour and asserts nothing. On CI that would silently turn
        // the entire baseline into a no-op — the one failure mode this suite cannot tolerate, because it would look
        // exactly like success. Regeneration is a deliberate local act whose output a human reviews in the diff.
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
            // Fall back to the classpath copy so the suite still runs when the working directory is not the module
            // root.
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
