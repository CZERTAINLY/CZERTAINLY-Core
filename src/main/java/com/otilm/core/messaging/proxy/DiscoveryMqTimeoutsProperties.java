package com.otilm.core.messaging.proxy;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Per-operation timeout budgets for the discovery v2 MQ client — bound from {@code discovery.mq-timeouts.*} so a
 * deployment whose connectors return large drain batches can raise the drain budget without touching the proxy-wide
 * request timeout. A missing key fails at binding with the full property path; positivity is enforced by
 * {@code DiscoveryMqTimeouts} itself at bean construction.
 */
@ConfigurationProperties(prefix = "discovery.mq-timeouts")
@Validated
public record DiscoveryMqTimeoutsProperties(@NotNull Duration status, @NotNull Duration drain,
        @NotNull Duration control) {
}
