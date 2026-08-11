package com.otilm.core.config;

import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tuning for the shared WebClient — response/connect timeouts and the connection pool — used for all outbound
 * API-client calls. Bound from {@code connector.api-client.*}; validated at startup so misconfiguration fails fast with
 * a clear error rather than a runtime NPE or an invalid pool.
 *
 * <p>
 * The {@code application.yml} defaults mirror {@code ClientTuning.defaults()} in interfaces (tests and untuned callers
 * use those; deployments use these). Keep the two in sync.
 */
@ConfigurationProperties(prefix = "connector.api-client")
@Validated
public record ConnectorApiClientProperties(Duration connectTimeout, Duration responseTimeout,
        @Positive int maxConnections, Duration pendingAcquireTimeout) {

    public ConnectorApiClientProperties {
        // Strictly positive, not merely non-null: 0s/negative would disable timeouts or fail deeper
        // in WebClient setup — fail fast at binding instead.
        requirePositive(connectTimeout, "connect-timeout");
        requirePositive(responseTimeout, "response-timeout");
        requirePositive(pendingAcquireTimeout, "pending-acquire-timeout");
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(
                    "connector.api-client." + name + " must be a positive duration, was " + value);
        }
    }
}
