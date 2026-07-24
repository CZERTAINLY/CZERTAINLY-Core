package com.otilm.core.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Tuning for the shared WebClient — response/connect timeouts and the connection pool — used for all
 * outbound API-client calls. Bound from {@code connector.client.*}; validated at startup so
 * misconfiguration fails fast with a clear error rather than a runtime NPE or an invalid pool.
 */
@ConfigurationProperties(prefix = "connector.client")
@Validated
public record ConnectorClientProperties(
        @NotNull Duration connectTimeout,
        @NotNull Duration responseTimeout,
        @Positive int maxConnections,
        @NotNull Duration pendingAcquireTimeout) {
}
