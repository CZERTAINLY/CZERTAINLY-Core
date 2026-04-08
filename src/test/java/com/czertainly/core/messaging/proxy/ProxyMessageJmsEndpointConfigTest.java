package com.czertainly.core.messaging.proxy;

import com.czertainly.api.clients.mq.model.ProxyMessage;
import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import com.czertainly.core.messaging.jms.listeners.MessageProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.retry.support.RetryTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProxyMessageJmsEndpointConfig}.
 * Tests ServiceBus vs RabbitMQ specific endpoint configuration.
 */
@ExtendWith(MockitoExtension.class)
class ProxyMessageJmsEndpointConfigTest {

    @Mock
    private MessageProcessor<ProxyMessage> messageProcessor;

    @Mock
    private MessagingProperties messagingProperties;

    @Mock
    private RetryTemplate retryTemplate;

    private ProxyProperties proxyProperties;

    @BeforeEach
    void setUp() {
        proxyProperties = new ProxyProperties(
            "test-proxy-exchange",
                "test-core-queue",
                "test-instance",
                Duration.ofSeconds(30),
                1000,
                null
        );
    }

    @Test
    void listenerEndpoint_withServiceBus_configuresSubscriptionAndSelector() {
        when(messagingProperties.brokerType()).thenReturn(MessagingProperties.BrokerType.SERVICEBUS);

        ProxyMessageJmsEndpointConfig config = new ProxyMessageJmsEndpointConfig(new ObjectMapper(), messageProcessor, retryTemplate, messagingProperties, proxyProperties);
        SimpleJmsListenerEndpoint endpoint = config.listenerEndpoint();

        assertThat(endpoint.getDestination()).isEqualTo(proxyProperties.exchange());
        assertThat(endpoint.getSubscription()).isEqualTo(proxyProperties.responseQueue());
        assertThat(endpoint.getSelector()).isNull();
    }

    @Test
    void listenerEndpoint_withRabbitMQ_noSubscriptionOrSelector() {
        String destination = "/queues/" + proxyProperties.responseQueue();

        when(messagingProperties.brokerType()).thenReturn(MessagingProperties.BrokerType.RABBITMQ);

        ProxyMessageJmsEndpointConfig config = new ProxyMessageJmsEndpointConfig(new ObjectMapper(), messageProcessor, retryTemplate, messagingProperties, proxyProperties);
        SimpleJmsListenerEndpoint endpoint = config.listenerEndpoint();

        assertThat(endpoint.getDestination()).isEqualTo(destination);
        assertThat(endpoint.getSubscription()).isNull();
        assertThat(endpoint.getSelector()).isNull();
    }
}
