package com.czertainly.core.messaging.proxy;

import com.czertainly.api.clients.mq.model.ProxyMessage;
import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import com.czertainly.core.messaging.jms.listeners.AbstractJmsEndpointConfig;
import com.czertainly.core.messaging.jms.listeners.MessageProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * JMS endpoint configuration for receiving proxy messages.
 *
 * <p>Configures a listener that subscribes to proxy messages.
 * For Azure ServiceBus, filtering is configured via subscription filters in Azure.</p>
 */
@Component
@Profile("!test")
public class ProxyMessageJmsEndpointConfig extends AbstractJmsEndpointConfig<ProxyMessage> {

    private final ProxyProperties proxyProperties;

    public ProxyMessageJmsEndpointConfig(
        ObjectMapper objectMapper,
        MessageProcessor<ProxyMessage> listenerMessageProcessor,
        RetryTemplate jmsRetryTemplate,
        MessagingProperties messagingProperties,
        ProxyProperties proxyProperties) {
        super(objectMapper, listenerMessageProcessor, jmsRetryTemplate, messagingProperties);
        this.proxyProperties = proxyProperties;
    }

    @Override
    public SimpleJmsListenerEndpoint listenerEndpoint() {
        return listenerEndpointInternal(
            "proxyMessageListener",
            // messagingProperties.consumerDestination cannot be used here because the destination for SERVICEBUS is taken from messagingProperties.exchange()
            messagingProperties.brokerType() == MessagingProperties.BrokerType.SERVICEBUS
                 ? proxyProperties.exchange()
                : "/queues/" + proxyProperties.responseQueue(),
            proxyProperties.responseQueue(),
            null,
            proxyProperties.concurrency(),
            ProxyMessage.class
        );
    }

}
