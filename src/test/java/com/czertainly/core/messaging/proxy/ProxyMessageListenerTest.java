package com.czertainly.core.messaging.proxy;

import com.czertainly.api.clients.mq.model.ConnectorResponse;
import com.czertainly.api.clients.mq.model.ProxyMessage;
import com.czertainly.api.exception.MessageHandlingException;
import com.czertainly.core.messaging.proxy.handler.MessageTypeHandlerRegistry;
import com.czertainly.core.messaging.proxy.redis.RedisResponseDistributor;
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
 * Unit tests for {@link ProxyMessageListener}.
 * Tests the three-tier response routing: handler → correlator → Redis distribution.
 */
@ExtendWith(MockitoExtension.class)
class ProxyMessageListenerTest {

    @Mock
    private ProxyMessageCorrelator correlator;

    @Mock
    private MessageTypeHandlerRegistry handlerRegistry;

    @Mock
    private RedisResponseDistributor redisDistributor;

    private ProxyMessageListener listener;

    @BeforeEach
    void setUp() {
        listener = new ProxyMessageListener(correlator, handlerRegistry, redisDistributor);
    }

    // ==================== Null Handling Tests ====================

    @Test
    void processMessage_withNullMessage_logsWarningAndReturns() {
        // Should not throw
        assertThatCode(() -> listener.processMessage(null)).doesNotThrowAnyException();

        verifyNoInteractions(handlerRegistry);
        verifyNoInteractions(correlator);
        verifyNoInteractions(redisDistributor);
    }

    // ==================== Handler-Based Routing (Tier 1) Tests ====================

    @Test
    void processMessage_withRegisteredHandler_dispatchesToHandler() throws MessageHandlingException {
        ProxyMessage message = createMessage("corr-1", "certificate.issued");
        when(handlerRegistry.hasHandler("certificate.issued")).thenReturn(true);
        when(handlerRegistry.dispatch(message)).thenReturn(true);

        listener.processMessage(message);

        verify(handlerRegistry).hasHandler("certificate.issued");
        verify(handlerRegistry).dispatch(message);
    }

    @Test
    void processMessage_withRegisteredHandler_doesNotCallCorrelator() throws MessageHandlingException {
        ProxyMessage message = createMessage("corr-1", "certificate.issued");
        when(handlerRegistry.hasHandler("certificate.issued")).thenReturn(true);
        when(handlerRegistry.dispatch(message)).thenReturn(true);

        listener.processMessage(message);

        verify(correlator, never()).tryCompleteRequest(any());
        verify(redisDistributor, never()).publishResponse(any());
    }

    @Test
    void processMessage_handlerNotFound_fallsToCorrelator() throws MessageHandlingException {
        ProxyMessage message = createMessage("corr-1", "unknown.type");
        when(handlerRegistry.hasHandler("unknown.type")).thenReturn(false);
        when(correlator.tryCompleteRequest(message)).thenReturn(true);

        listener.processMessage(message);

        verify(correlator).tryCompleteRequest(message);
    }

    @Test
    void processMessage_withNullHandlerRegistry_skipsHandlerCheck() throws MessageHandlingException {
        listener = new ProxyMessageListener(correlator, null, redisDistributor);
        ProxyMessage message = createMessage("corr-1", "some.type");
        when(correlator.tryCompleteRequest(message)).thenReturn(true);

        listener.processMessage(message);

        verify(correlator).tryCompleteRequest(message);
    }

    @Test
    void processMessage_withNullMessageType_skipsHandlerCheck() throws MessageHandlingException {
        ProxyMessage message = createMessage("corr-1", null);
        when(correlator.tryCompleteRequest(message)).thenReturn(true);

        listener.processMessage(message);

        // Handler check should be skipped when messageType is null
        verify(handlerRegistry, never()).hasHandler(any());
        verify(correlator).tryCompleteRequest(message);
    }

    // ==================== Correlator-Based Routing (Tier 2) Tests ====================

    @Test
    void processMessage_withLocalCorrelation_completesLocally() throws MessageHandlingException {
        ProxyMessage message = createMessage("corr-local", "GET:/v1/test");
        when(handlerRegistry.hasHandler("GET:/v1/test")).thenReturn(false);
        when(correlator.tryCompleteRequest(message)).thenReturn(true);

        listener.processMessage(message);

        verify(correlator).tryCompleteRequest(message);
        verify(redisDistributor, never()).publishResponse(any());
    }

    @Test
    void processMessage_locallyHandled_doesNotDistributeViaRedis() throws MessageHandlingException {
        ProxyMessage message = createMessage("corr-local", "POST:/v1/data");
        when(handlerRegistry.hasHandler("POST:/v1/data")).thenReturn(false);
        when(correlator.tryCompleteRequest(message)).thenReturn(true);

        listener.processMessage(message);

        verify(redisDistributor, never()).publishResponse(any());
    }

    // ==================== Redis Distribution (Tier 3) Tests ====================

    @Test
    void processMessage_notFoundLocally_distributesViaRedis() throws MessageHandlingException {
        ProxyMessage message = createMessage("corr-remote", "GET:/v1/resource");
        when(handlerRegistry.hasHandler("GET:/v1/resource")).thenReturn(false);
        when(correlator.tryCompleteRequest(message)).thenReturn(false);

        listener.processMessage(message);

        verify(redisDistributor).publishResponse(message);
    }

    @Test
    void processMessage_noRedisDistributor_logsWarning() throws MessageHandlingException {
        listener = new ProxyMessageListener(correlator, handlerRegistry, null);
        ProxyMessage message = createMessage("corr-remote", "GET:/v1/resource");
        when(handlerRegistry.hasHandler("GET:/v1/resource")).thenReturn(false);
        when(correlator.tryCompleteRequest(message)).thenReturn(false);

        // Should not throw, just log warning
        assertThatCode(() -> listener.processMessage(message)).doesNotThrowAnyException();
    }

    @Test
    void processMessage_withNullCorrelationId_logsWarningAndReturns() throws MessageHandlingException {
        ProxyMessage message = createMessage(null, "GET:/v1/test");
        when(handlerRegistry.hasHandler("GET:/v1/test")).thenReturn(false);

        // Should not throw, just log warning
        assertThatCode(() -> listener.processMessage(message)).doesNotThrowAnyException();

        verify(correlator, never()).tryCompleteRequest(any());
        verify(redisDistributor, never()).publishResponse(any());
    }

    @Test
    void processMessage_withBlankCorrelationId_logsWarningAndReturns() throws MessageHandlingException {
        ProxyMessage message = createMessage("   ", "GET:/v1/test");
        when(handlerRegistry.hasHandler("GET:/v1/test")).thenReturn(false);

        // Should not throw, just log warning
        assertThatCode(() -> listener.processMessage(message)).doesNotThrowAnyException();

        verify(correlator, never()).tryCompleteRequest(any());
        verify(redisDistributor, never()).publishResponse(any());
    }

    // ==================== Error Handling Tests ====================

    @Test
    void processMessage_onHandlerException_throwsMessageHandlingException() {
        ProxyMessage message = createMessage("corr-1", "error.type");
        when(handlerRegistry.hasHandler("error.type")).thenReturn(true);
        when(handlerRegistry.dispatch(message)).thenThrow(new RuntimeException("Handler failed"));

        assertThatThrownBy(() -> listener.processMessage(message))
                .isInstanceOf(MessageHandlingException.class)
                .hasMessageContaining("Failed to process proxy message");
    }

    @Test
    void processMessage_onCorrelatorException_throwsMessageHandlingException() {
        ProxyMessage message = createMessage("corr-1", "test.type");
        when(handlerRegistry.hasHandler("test.type")).thenReturn(false);
        when(correlator.tryCompleteRequest(message)).thenThrow(new RuntimeException("Correlator failed"));

        assertThatThrownBy(() -> listener.processMessage(message))
                .isInstanceOf(MessageHandlingException.class)
                .hasMessageContaining("Failed to process proxy message");
    }

    @Test
    void processMessage_onRedisException_throwsMessageHandlingException() {
        ProxyMessage message = createMessage("corr-1", "test.type");
        when(handlerRegistry.hasHandler("test.type")).thenReturn(false);
        when(correlator.tryCompleteRequest(message)).thenReturn(false);
        doThrow(new RuntimeException("Redis failed")).when(redisDistributor).publishResponse(message);

        assertThatThrownBy(() -> listener.processMessage(message))
                .isInstanceOf(MessageHandlingException.class)
                .hasMessageContaining("Failed to process proxy message");
    }

    // ==================== Helper Methods ====================

    private ProxyMessage createMessage(String correlationId, String messageType) {
        return ProxyMessage.builder()
                .correlationId(correlationId)
                .proxyId("test-proxy")
                .messageType(messageType)
                .timestamp(Instant.now())
                .connectorResponse(ConnectorResponse.builder()
                        .statusCode(200)
                        .build())
                .build();
    }
}
