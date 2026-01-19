package com.czertainly.core.messaging.jms.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.apache.commons.lang3.StringUtils;
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
        String user,      // Required for RabbitMQ and ServiceBus+SAS, optional for ServiceBus+AAD
        String password,  // Required for RabbitMQ and ServiceBus+SAS, optional for ServiceBus+AAD
        @Valid AadAuth aadAuth,
        @Valid Listener listener,
        @Valid Producer producer,
        @Valid Queue queue,
        @NotNull @Valid RoutingKey routingKey
) {

    /**
     * Compact constructor that validates authentication configuration based on broker type.
     */
    public MessagingProperties {
        boolean hasUserAndPassword = StringUtils.isNotBlank(user) && StringUtils.isNotBlank(password);
        boolean hasAadAuth = aadAuth != null && aadAuth.isEnabled();

        switch (brokerType) {
            case RABBITMQ -> {
                if (!hasUserAndPassword) {
                    throw new IllegalArgumentException(
                            "RabbitMQ requires BROKER_USER and BROKER_PASSWORD to be configured");
                }
            }
            case SERVICEBUS -> {
                if (!hasUserAndPassword && !hasAadAuth) {
                    throw new IllegalArgumentException(
                            "ServiceBus requires either BROKER_USER/BROKER_PASSWORD (SAS) " +
                                    "or BROKER_AZURE_TENANT_ID/BROKER_AZURE_CLIENT_ID/BROKER_AZURE_CLIENT_SECRET (AAD) to be configured");
                }
            }
        }
    }

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

    /**
     * AAD (Azure Active Directory) authentication configuration for ServiceBus.
     * When enabled is true, the connection will use OAuth2 token authentication
     * instead of SAS token (user/password).
     */
    public record AadAuth(
            String tenantId,
            String clientId,
            String clientSecret,
            int tokenRefreshInterval,
            int tokenGettingTimeout
    ) {
        public boolean isEnabled() {
            return StringUtils.isNotBlank(tenantId) && StringUtils.isNotBlank(clientId) && StringUtils.isNotBlank(clientSecret);
        }
    }

    public enum BrokerType {
        RABBITMQ,
        SERVICEBUS
    }
}
