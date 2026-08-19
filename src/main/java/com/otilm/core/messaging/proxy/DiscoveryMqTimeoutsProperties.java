package com.otilm.core.messaging.proxy;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Per-operation timeout budgets for the discovery v2 MQ client — bound from {@code discovery.mq-timeouts.*} so a
 * deployment whose connectors return large drain batches can raise the drain budget without touching the proxy-wide
 * request timeout. Positivity is enforced by {@code DiscoveryMqTimeouts} itself at bean construction.
 */
@ConfigurationProperties(prefix = "discovery.mq-timeouts")
@Validated
public record DiscoveryMqTimeoutsProperties(Duration status, Duration drain, Duration control) {
}
