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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    private ProxyMessageJmsEndpointConfig config;

    @BeforeEach
    void setUp() {
        ProxyProperties proxyProperties = new ProxyProperties(
                "test-proxy-exchange",
                "test-core-queue",
                Duration.ofSeconds(30),
                1000,
                null
        );

        config = new ProxyMessageJmsEndpointConfig(proxyProperties);
        ReflectionTestUtils.setField(config, "listenerMessageProcessor", messageProcessor);
        ReflectionTestUtils.setField(config, "jmsRetryTemplate", retryTemplate);
        ReflectionTestUtils.setField(config, "messagingProperties", messagingProperties);
        ReflectionTestUtils.setField(config, "objectMapper", new ObjectMapper());
    }

    @Test
    void listenerEndpoint_withServiceBus_configuresSubscriptionAndSelector() {
        when(messagingProperties.brokerType()).thenReturn(MessagingProperties.BrokerType.SERVICEBUS);

        SimpleJmsListenerEndpoint endpoint = config.listenerEndpoint();

        assertThat(endpoint.getDestination()).isEqualTo("test-proxy-exchange");
        assertThat(endpoint.getSubscription()).isEqualTo("test-core-queue");
        assertThat(endpoint.getSelector()).isNotNull();
        assertThat(endpoint.getSelector()).contains("test-core-queue");
    }

    @Test
    void listenerEndpoint_withRabbitMQ_noSubscriptionOrSelector() {
        when(messagingProperties.brokerType()).thenReturn(MessagingProperties.BrokerType.RABBITMQ);

        SimpleJmsListenerEndpoint endpoint = config.listenerEndpoint();

        assertThat(endpoint.getDestination()).isEqualTo("test-proxy-exchange");
        assertThat(endpoint.getSubscription()).isNull();
        assertThat(endpoint.getSelector()).isNull();
    }
}
