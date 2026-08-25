package com.otilm.core.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts that no shipped configuration turns on Hibernate's bound-parameter logging.
 *
 * <p>
 * {@code org.hibernate.orm.jdbc.bind} at TRACE prints every value bound to every statement. Among those values is
 * {@code crypto_asset.identity_key}, whose whole protection is that it never leaves the database: a log line is the
 * same disclosure as a response field, and logs travel further. The rule covers the ancestors too -- a root logger at
 * TRACE enables the binding logger just as surely as naming it does.
 *
 * <p>
 * Deploy-time overrides are outside this test's reach; what it can and does pin is that nothing we ship, and no default
 * of an environment variable we ship, arrives at TRACE.
 */
class HibernateBindingLogConfigTest {

    private static final Path SHIPPED_CONFIG_ROOT = Path.of("src/main/resources");

    private static final Path LOGBACK_CONFIG = Path.of("src/main/resources/logback-spring.xml");

    private static final String LOGGING_LEVEL_PREFIX = "logging.level.";

    /** Loggers that print bound parameters: the Hibernate 6 name, and the Hibernate 5 name still seen in the wild. */
    private static final List<String> BINDING_LOGGERS = List
            .of("org.hibernate.orm.jdbc.bind", "org.hibernate.type.descriptor.sql.BasicBinder");

    private static final String HIBERNATE_ROOT = "org.hibernate";

    private static final Set<String> LEVELS_THAT_PRINT_BINDINGS = Set.of("TRACE", "ALL");

    /** {@code ${ENV_VAR:default}} -- what matters is the default, which is what ships. */
    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{[^:}]*(?::(.*))?}$");

    private static final Pattern LOGBACK_LOGGER = Pattern
            .compile("<logger\\s+[^>]*name\\s*=\\s*\"([^\"]*)\"[^>]*level\\s*=\\s*\"([^\"]*)\"");

    private static final Pattern LOGBACK_ROOT = Pattern.compile("<root\\s+[^>]*level\\s*=\\s*\"([^\"]*)\"");

    // ---- the rule ----

    /**
     * Whether a logger configured at TRACE could put a bound parameter in a log.
     *
     * <p>
     * True for the binding loggers themselves, for every ancestor of one (root included), and for anything below one.
     * True also for anything else inside {@code org.hibernate}: the rule is stated over the whole Hibernate tree rather
     * than the two exact logger names, because a TRACE level inside persistence is never worth the risk of a renamed
     * category, and drawing the line finely is how a fence acquires a hole.
     */
    static boolean mayPrintBoundParameters(String loggerName) {
        String logger = loggerName == null || loggerName.isBlank() || "root".equalsIgnoreCase(loggerName)
                ? ""
                : loggerName.trim();
        if (logger.isEmpty()) {
            return true;
        }
        if (logger.equals(HIBERNATE_ROOT) || logger.startsWith(HIBERNATE_ROOT + ".")) {
            return true;
        }
        return BINDING_LOGGERS.stream().anyMatch(target -> target.startsWith(logger + "."));
    }

    /** The configured level as shipped: a placeholder resolves to its default, and the comparison is locale-free. */
    static String shippedLevel(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        Matcher placeholder = PLACEHOLDER.matcher(rawValue.trim());
        String value = placeholder.matches() ? String.valueOf(placeholder.group(1)) : rawValue.trim();
        return value.toUpperCase(Locale.ROOT);
    }

    static List<String> bindingLogViolations(String source, Map<String, String> loggerLevels) {
        List<String> violations = new ArrayList<>();
        loggerLevels.forEach((logger, rawLevel) -> {
            String level = shippedLevel(rawLevel);
            if (LEVELS_THAT_PRINT_BINDINGS.contains(level) && mayPrintBoundParameters(logger)) {
                violations
                        .add(source + " sets logger '" + logger + "' to " + level
                                + ", which prints every bound parameter");
            }
        });
        return violations;
    }

    // ---- the rule, applied to what ships ----

    @Test
    void noShippedYamlEnablesBoundParameterLogging() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SHIPPED_CONFIG_ROOT)) {
            for (Path file : files.filter(HibernateBindingLogConfigTest::isYaml).sorted().toList()) {
                violations.addAll(bindingLogViolations(file.toString(), loggingLevelsOf(file)));
            }
        }

        assertThat(violations)
                .describedAs("a bound-parameter log line discloses the crypto-asset identity key as surely as a "
                        + "response field does")
                .isEmpty();
    }

    @Test
    void logbackDoesNotEnableBoundParameterLogging() throws IOException {
        String logback = Files.readString(LOGBACK_CONFIG, StandardCharsets.UTF_8);
        Map<String, String> levels = new LinkedHashMap<>();
        Matcher loggers = LOGBACK_LOGGER.matcher(logback);
        while (loggers.find()) {
            levels.put(loggers.group(1), loggers.group(2));
        }
        Matcher root = LOGBACK_ROOT.matcher(logback);
        while (root.find()) {
            levels.put("root", root.group(1));
        }

        assertThat(levels).describedAs("the logback configuration must declare at least the root level").isNotEmpty();
        assertThat(bindingLogViolations(LOGBACK_CONFIG.toString(), levels)).isEmpty();
    }

    // ---- the rule, shown able to fail ----

    @Test
    void aPlantedBindingLoggerIsReported() {
        assertThat(bindingLogViolations("planted.yml", Map.of("org.hibernate.orm.jdbc.bind", "TRACE"))).hasSize(1);
        assertThat(bindingLogViolations("planted.yml", Map.of("org.hibernate", "trace"))).hasSize(1);
        assertThat(bindingLogViolations("planted.yml", Map.of("org", "TRACE"))).hasSize(1);
        assertThat(bindingLogViolations("planted.yml", Map.of("root", "TRACE"))).hasSize(1);
        assertThat(bindingLogViolations("planted.yml", Map.of("org.hibernate.SQL", "${LOG_LEVEL:ALL}"))).hasSize(1);
        assertThat(bindingLogViolations("planted.yml", Map.of("org.hibernate.orm", "TRACE"))).hasSize(1);
    }

    @Test
    void legitimateLevelsAreNotReported() {
        assertThat(bindingLogViolations("planted.yml", Map.of("org.hibernate", "WARN"))).isEmpty();
        assertThat(bindingLogViolations("planted.yml", Map.of("org.hibernate.SQL", "DEBUG"))).isEmpty();
        assertThat(bindingLogViolations("planted.yml", Map.of("com.otilm", "TRACE")))
                .describedAs("our own loggers at TRACE do not print Hibernate's bindings")
                .isEmpty();
        assertThat(bindingLogViolations("planted.yml", Map.of("com.otilm", "${PLATFORM_LOG_LEVEL:INFO}"))).isEmpty();
    }

    // ---- helpers ----

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static Map<String, String> loggingLevelsOf(Path yaml) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new FileSystemResource(yaml));
        factory.afterPropertiesSet();
        Properties properties = factory.getObject();
        Map<String, String> levels = new LinkedHashMap<>();
        if (properties == null) {
            return levels;
        }
        properties
                .stringPropertyNames()
                .stream()
                .filter(key -> key.startsWith(LOGGING_LEVEL_PREFIX))
                .forEach(key -> levels.put(key.substring(LOGGING_LEVEL_PREFIX.length()), properties.getProperty(key)));
        return levels;
    }
}
