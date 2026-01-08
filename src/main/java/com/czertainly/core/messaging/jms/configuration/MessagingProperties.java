package com.czertainly.core.messaging.jms.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "spring.messaging")
@Validated
public record MessagingProperties(
        @NotNull MessagingProperties.BrokerType brokerType,
        @NotBlank String brokerUrl,
        @Positive int sessionCacheSize,
        @NotBlank String exchange,
        String vhost,
        @NotBlank String user,
        @NotBlank String password,
        @Valid Listener listener,
        @Valid Producer producer,
        @Valid Queue queue,
        @NotNull @Valid RoutingKey routingKey
) {
    private String producerDestination(String routingKey) {
        if (brokerType == BrokerType.SERVICEBUS) {
            return exchange();
        }
        return "/exchanges/" + exchange() + "/" + routingKey;
    }

    public String consumerDestination(String queueName) {
        if (brokerType == BrokerType.SERVICEBUS) {
            return exchange();
        }
        return "/queues/" + queueName;
    }

    public String produceDestinationActions() {
        return producerDestination(routingKey().actions());
    }

    public String produceDestinationAuditLogs() {
        return producerDestination(routingKey().auditLogs());
    }

    public String produceDestinationEvent() {
        return producerDestination(routingKey().event());
    }

    public String produceDestinationNotifications() {
        return producerDestination(routingKey().notification());
    }

    public String produceDestinationScheduler() {
        return producerDestination(routingKey().scheduler());
    }

    public String produceDestinationValidation() {
        return producerDestination(routingKey().validation());
    }

    public record Queue (
            @NotBlank String actions,
            @NotBlank String auditLogs,
            @NotBlank String event,
            @NotBlank String notification,
            @NotBlank String scheduler,
            @NotBlank String validation
    ) {}

    public record RoutingKey(
            @NotBlank String actions,
            @NotBlank String auditLogs,
            @NotBlank String event,
            @NotBlank String notification,
            @NotBlank String scheduler,
            @NotBlank String validation
    ) {}

    public record Listener(
            Long recoveryInterval
    ) {}

    public record Producer(
            @NotNull @Valid Retry retry
    ) {}

    public record Retry(
            @NotNull Boolean enabled,
            @NotNull @Positive Long initialInterval,
            @NotNull @Positive Integer maxAttempts,
            @NotNull @Positive Long maxInterval,
            @NotNull @Positive Long multiplier
    ) {
        public Retry {
            if (enabled == null) enabled = true;
            if (initialInterval == null) initialInterval = 3000L;
            if (maxAttempts == null) maxAttempts = 3;
            if (maxInterval == null) maxInterval = 10000L;
            if (multiplier == null) multiplier = 2L;
        }
    }

    public enum BrokerType {
        RABBITMQ,
        SERVICEBUS
    }
}
