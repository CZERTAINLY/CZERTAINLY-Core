package com.otilm.core.util;

import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

final class TestCostRecorder {

    private static final String CI_TEST_GROUP = System.getenv("CI_TEST_GROUP");
    private static final boolean ENABLED = CI_TEST_GROUP != null && !CI_TEST_GROUP.isBlank();
    private static final Set<ApplicationContext> CONTEXTS =
            Collections.newSetFromMap(Collections.synchronizedMap(new IdentityHashMap<>()));
    private static final LongAdder DATABASE_RESET_NANOS = new LongAdder();
    private static final LongAdder SETTINGS_REFRESH_NANOS = new LongAdder();
    private static final LongAdder BASE_SETUP_INVOCATIONS = new LongAdder();

    static {
        if (ENABLED) {
            Runtime.getRuntime().addShutdownHook(new Thread(TestCostRecorder::writeSnapshot, "test-cost-recorder"));
        }
    }

    private TestCostRecorder() {
    }

    static void recordContext(ApplicationContext context) {
        if (ENABLED) {
            CONTEXTS.add(context);
        }
    }

    static void recordBaseSetup(long databaseResetNanos, long settingsRefreshNanos) {
        if (ENABLED) {
            BASE_SETUP_INVOCATIONS.increment();
            DATABASE_RESET_NANOS.add(databaseResetNanos);
            SETTINGS_REFRESH_NANOS.add(settingsRefreshNanos);
        }
    }

    private static void writeSnapshot() {
        Path output = Path.of(System.getProperty("user.dir"), "target", "test-costs", "harness.properties");
        String snapshot = """
                group=%s
                spring_contexts=%d
                base_setup_invocations=%d
                database_reset_seconds=%.3f
                settings_refresh_seconds=%.3f
                """.formatted(
                CI_TEST_GROUP,
                CONTEXTS.size(),
                BASE_SETUP_INVOCATIONS.sum(),
                DATABASE_RESET_NANOS.sum() / 1_000_000_000.0,
                SETTINGS_REFRESH_NANOS.sum() / 1_000_000_000.0);
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, snapshot);
        } catch (IOException e) {
            System.err.println("Failed to write test cost snapshot to " + output + ": " + e.getMessage());
        }
    }
}
