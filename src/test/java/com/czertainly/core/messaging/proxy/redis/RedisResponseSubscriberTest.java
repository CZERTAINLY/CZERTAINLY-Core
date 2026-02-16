package com.czertainly.core.messaging.proxy.redis;

import com.czertainly.api.clients.mq.model.ConnectorResponse;
import com.czertainly.api.clients.mq.model.ProxyMessage;
import com.czertainly.core.messaging.proxy.ProxyMessageCorrelator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RedisResponseSubscriber}.
 * Tests handling of Redis pub/sub messages and completion of local pending requests.
 */
@ExtendWith(MockitoExtension.class)
class RedisResponseSubscriberTest {

    @Mock
    private ProxyMessageCorrelator correlator;

    @Mock
    private ObjectMapper objectMapper;

    private RedisResponseSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new RedisResponseSubscriber(correlator, objectMapper);
    }

    @Test
    void onMessage_deserializesAndTriesToCompleteLocally() throws Exception {
        String jsonMessage = "{\"correlationId\":\"corr-1\",\"proxyId\":\"test-proxy\"}";
        ProxyMessage message = createMessage("corr-1");
        when(objectMapper.readValue(jsonMessage, ProxyMessage.class)).thenReturn(message);
        when(correlator.tryCompleteRequest(message)).thenReturn(true);

        subscriber.onMessage(jsonMessage);

        verify(objectMapper).readValue(jsonMessage, ProxyMessage.class);
        verify(correlator).tryCompleteRequest(message);
    }

    @Test
    void onMessage_whenNotFoundLocally_ignoresQuietly() throws Exception {
        String jsonMessage = "{\"correlationId\":\"corr-other-instance\"}";
        ProxyMessage message = createMessage("corr-other-instance");
        when(objectMapper.readValue(jsonMessage, ProxyMessage.class)).thenReturn(message);
        when(correlator.tryCompleteRequest(message)).thenReturn(false);

        assertThatCode(() -> subscriber.onMessage(jsonMessage)).doesNotThrowAnyException();
        verify(correlator).tryCompleteRequest(message);
    }

    @Test
    void onMessage_onDeserializationError_doesNotCallCorrelator() throws Exception {
        String invalidJson = "invalid json";
        when(objectMapper.readValue(invalidJson, ProxyMessage.class))
                .thenThrow(new JsonProcessingException("Parse error") {});

        assertThatCode(() -> subscriber.onMessage(invalidJson)).doesNotThrowAnyException();

        verify(correlator, never()).tryCompleteRequest(any());
    }

    @Test
    void onMessage_withNullCorrelationId_skipsCorrelator() throws Exception {
        String jsonMessage = "{\"correlationId\":null,\"proxyId\":\"test-proxy\"}";
        ProxyMessage message = createMessage(null);
        when(objectMapper.readValue(jsonMessage, ProxyMessage.class)).thenReturn(message);

        subscriber.onMessage(jsonMessage);

        verify(correlator, never()).tryCompleteRequest(any());
    }

    @Test
    void onMessage_onCorrelatorException_handlesGracefully() throws Exception {
        String jsonMessage = "{\"correlationId\":\"corr-error\"}";
        ProxyMessage message = createMessage("corr-error");
        when(objectMapper.readValue(jsonMessage, ProxyMessage.class)).thenReturn(message);
        when(correlator.tryCompleteRequest(message)).thenThrow(new IllegalStateException("Correlator error"));

        assertThatCode(() -> subscriber.onMessage(jsonMessage)).doesNotThrowAnyException();

        verify(correlator).tryCompleteRequest(message);
    }

    private ProxyMessage createMessage(String correlationId) {
        return ProxyMessage.builder()
                .correlationId(correlationId)
                .proxyId("test-proxy")
                .timestamp(Instant.now())
                .connectorResponse(ConnectorResponse.builder()
                        .statusCode(200)
                        .build())
                .build();
    }
}
