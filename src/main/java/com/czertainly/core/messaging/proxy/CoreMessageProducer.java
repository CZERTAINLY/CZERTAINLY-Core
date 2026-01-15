package com.czertainly.core.messaging.proxy;

import com.czertainly.api.clients.mq.model.CoreMessage;
import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * Produces core messages to the message queue for proxy consumption.
 * Sends requests to the appropriate proxy instance based on proxyId.
 */
@Slf4j
@Component
public class CoreMessageProducer {

    private final JmsTemplate jmsTemplate;
    private final ProxyProperties proxyProperties;
    private final MessagingProperties messagingProperties;
    private final RetryTemplate retryTemplate;

    public CoreMessageProducer(
            JmsTemplate jmsTemplate,
            ProxyProperties proxyProperties,
            MessagingProperties messagingProperties,
            RetryTemplate retryTemplate) {
        this.jmsTemplate = jmsTemplate;
        this.proxyProperties = proxyProperties;
        this.messagingProperties = messagingProperties;
        this.retryTemplate = retryTemplate;
        log.info("CoreMessageProducer initialized with exchange: {}", proxyProperties.exchange());
    }

    /**
     * Send a core message to the specified proxy instance.
     *
     * @param message The core message to send
     * @param proxyId The target proxy instance ID
     */
    public void send(CoreMessage message, String proxyId) {
        Objects.requireNonNull(message, "message must not be null");
        if (proxyId == null || proxyId.isBlank()) {
            throw new IllegalArgumentException("proxyId must not be null or blank");
        }

        String routingKey = proxyProperties.getRequestRoutingKey(proxyId);
        String destination = getDestination();

        log.debug("Sending core message correlationId={} proxyId={} destination={} routingKey={}",
                message.getCorrelationId(), proxyId, destination, routingKey);

        retryTemplate.execute(context -> {
            jmsTemplate.convertAndSend(
                    destination,
                    message,
                    msg -> {
                        // Azure-native: JMSType maps to Service Bus Label/Subject
                        // Use Label for routing - optimal with Correlation Filters
                        msg.setJMSType(routingKey);
                        // Set JMS correlation ID for request/response matching
                        msg.setJMSCorrelationID(message.getCorrelationId());
                        return msg;
                    });

            log.debug("Sent core message correlationId={} routingKey={}",
                    message.getCorrelationId(), routingKey);
            return null;
        });
    }

    /**
     * Get the destination (topic/exchange) based on broker type.
     * For ServiceBus, we use the topic directly.
     * For RabbitMQ, we prefix with the exchange if configured.
     */
    private String getDestination() {
        if (messagingProperties.brokerType() == MessagingProperties.BrokerType.SERVICEBUS) {
            return proxyProperties.exchange();
        }

        // For RabbitMQ, include exchange prefix
        return "/exchanges/" + proxyProperties.exchange();
    }
}
