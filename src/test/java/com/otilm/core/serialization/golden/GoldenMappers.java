package com.otilm.core.serialization.golden;

import com.otilm.core.config.WebAppConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Supplies the exact {@link ObjectMapper} instances production code serializes with.
 * <p>
 * A golden produced by a bespoke {@code new ObjectMapper()} would baseline a mapper nothing actually uses, and would
 * miss precisely the customizations most likely to shift under Jackson 3.
 */
final class GoldenMappers {

    private GoldenMappers() {
    }

    /**
     * The application's wire mapper, built through the very same factory method Spring calls.
     * {@code WebAppConfig#jsonObjectMapper} takes no collaborators, so invoking it directly yields a byte-identical
     * mapper with no application context — which is what keeps this suite off the test-context budget.
     * <p>
     * Its configuration is what these goldens ultimately pin: ISO-8601 dates rather than numeric timestamps, the
     * {@code JavaTimeModule}, {@code NON_NULL} inclusion (so absent fields are omitted, not rendered as null), and
     * lenient handling of a missing external type id.
     */
    static ObjectMapper web() {
        return new WebAppConfig().jsonObjectMapper();
    }

    /**
     * A bare mapper mirroring {@code AcmeJsonProcessor}'s private static instance, which parses ACME JWS payloads
     * outside the Spring-managed mapper entirely.
     * <p>
     * The divergence is deliberate to record, not to hide: ACME payloads are parsed with Jackson defaults, so they
     * do <i>not</i> inherit {@code NON_NULL} inclusion or the {@code JavaTimeModule}. Anything that consolidates the
     * 27 bespoke mapper sites (the follow-up prep item on #1941) will change ACME's on-wire output unless it
     * preserves this, and these goldens are what will say so.
     */
    static ObjectMapper acme() {
        return new ObjectMapper();
    }
}
