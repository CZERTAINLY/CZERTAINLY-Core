package com.czertainly.core.architecture;

import com.czertainly.core.security.authz.ExternalAuthorizationMissing;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalAuthorizationMissingRatchetTest {

    @Test
    void missing_annotation_count_must_not_exceed_ratchet() throws IOException {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.czertainly.core.service");

        long actual = classes.stream()
                .filter(c -> c.isInterface() && c.getSimpleName().endsWith("ExternalService"))
                .flatMap(c -> c.getMethods().stream())
                .filter(m -> m.isAnnotatedWith(ExternalAuthorizationMissing.class))
                .count();

        String raw;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("external-authorization-missing-count.txt")) {
            if (is == null) throw new IllegalStateException("external-authorization-missing-count.txt not found on classpath");
            raw = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        int maxAllowed;
        try {
            maxAllowed = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "external-authorization-missing-count.txt must contain a single integer, got: [" + raw + "]", e);
        }

        assertTrue(actual <= maxAllowed,
                "@ExternalAuthorizationMissing count is " + actual + " but ratchet allows at most " + maxAllowed);
    }
}
