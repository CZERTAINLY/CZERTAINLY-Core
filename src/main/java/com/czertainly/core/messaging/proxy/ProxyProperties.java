package com.czertainly.core.messaging.proxy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration properties for the proxy client.
 * These properties control how the ProxyClient communicates with connectors
 * via the message queue proxy.
 */
@ConfigurationProperties(prefix = "proxy", ignoreInvalidFields = true, ignoreUnknownFields = true)
@Validated
public record ProxyProperties(
        /**
         * Azure Service Bus topic / RabbitMQ exchange name for proxy communication.
         * Default: czertainly-proxy
         */
        String exchange,

        /**
         * Azure Service Bus subscription / RabbitMQ queue name for receiving responses from proxy.
         * Default: core
         */
        String responseQueue,

        /**
         * Default request timeout duration.
         * Default: 30 seconds
         */
        Duration requestTimeout,

        /**
         * Maximum number of pending requests allowed.
         * Prevents memory issues from too many outstanding requests.
         * Default: 1000
         */
        Integer maxPendingRequests,

        /**
         * Redis configuration for distributed response coordination.
         */
        RedisProperties redis
) {
    /**
     * Redis configuration for multi-instance proxy response distribution.
     */
    public record RedisProperties(
            /**
             * Redis pub/sub channel name for distributing proxy responses.
             * Default: proxy:responses
             */
            String channel,

            /**
             * Whether Redis-based response distribution is enabled.
             * Default: true
             */
            Boolean enabled
    ) {
        public RedisProperties {
            if (channel == null) {
                channel = "proxy:responses";
            }
            if (enabled == null) {
                enabled = true;
            }
        }
    }

    /**
     * Default constructor with default values.
     */
    public ProxyProperties {
        if (exchange == null) {
            exchange = "czertainly-proxy";
        }
        if (responseQueue == null) {
            responseQueue = "core";
        }
        if (requestTimeout == null) {
            requestTimeout = Duration.ofSeconds(30);
        }
        if (maxPendingRequests == null) {
            maxPendingRequests = 1000;
        }
        if (redis == null) {
            redis = new RedisProperties(null, null);
        }
    }

    /**
     * Get the request routing key/subject for a specific proxy instance.
     * @param proxyId The proxy instance ID
     * @return Routing key in format "coremessage.{proxyId}"
     */
    public String getRequestRoutingKey(String proxyId) {
        if (proxyId == null) {
            throw new IllegalArgumentException("proxyId must not be null");
        }
        return "coremessage." + proxyId;
    }

    /**
     * Get the response routing key pattern.
     * @return Pattern for matching all responses "response%"
     */
    public String getResponseRoutingKeyPattern() {
        return "response%";
    }
}
