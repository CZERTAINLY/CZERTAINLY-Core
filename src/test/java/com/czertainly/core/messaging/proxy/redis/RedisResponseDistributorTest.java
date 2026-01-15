package com.czertainly.core.messaging.proxy.redis;

import com.czertainly.api.clients.mq.model.ConnectorResponse;
import com.czertainly.api.clients.mq.model.ProxyMessage;
import com.czertainly.core.messaging.proxy.ProxyProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RedisResponseDistributor}.
 * Tests publishing proxy messages to Redis pub/sub channel.
 */
@ExtendWith(MockitoExtension.class)
class RedisResponseDistributorTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private RedisResponseDistributor distributor;

    @BeforeEach
    void setUp() {
        ProxyProperties.RedisProperties redisProps = new ProxyProperties.RedisProperties(
                "proxy:test-responses",
                true
        );
        ProxyProperties proxyProperties = new ProxyProperties(
                "test-exchange",
                "test-queue",
                Duration.ofSeconds(30),
                1000,
                redisProps
        );

        distributor = new RedisResponseDistributor(redisTemplate, objectMapper, proxyProperties);
    }

    @Test
    void publishResponse_serializesAndPublishesToConfiguredChannel() throws JsonProcessingException {
        ProxyMessage message = createMessage("corr-1");
        when(objectMapper.writeValueAsString(message)).thenReturn("{\"correlationId\":\"corr-1\"}");

        distributor.publishResponse(message);

        verify(objectMapper).writeValueAsString(message);
        verify(redisTemplate).convertAndSend(eq("proxy:test-responses"), eq("{\"correlationId\":\"corr-1\"}"));
    }

    @Test
    void publishResponse_onSerializationError_doesNotPublish() throws JsonProcessingException {
        ProxyMessage message = createMessage("corr-1");
        when(objectMapper.writeValueAsString(message)).thenThrow(new JsonProcessingException("Serialization failed") {});

        assertThatCode(() -> distributor.publishResponse(message)).doesNotThrowAnyException();

        verify(redisTemplate, never()).convertAndSend(any(), any());
    }

    @Test
    void publishResponse_onRedisConnectionError_handlesGracefully() throws JsonProcessingException {
        ProxyMessage message = createMessage("corr-1");
        when(objectMapper.writeValueAsString(message)).thenReturn("{}");
        doThrow(new RedisConnectionFailureException("Connection lost")).when(redisTemplate).convertAndSend(any(), any());

        assertThatCode(() -> distributor.publishResponse(message)).doesNotThrowAnyException();
    }

    @Test
    void publishResponse_withNullCorrelationId_stillPublishes() throws JsonProcessingException {
        ProxyMessage message = createMessage(null);
        when(objectMapper.writeValueAsString(message)).thenReturn("{\"correlationId\":null}");

        distributor.publishResponse(message);

        verify(redisTemplate).convertAndSend(eq("proxy:test-responses"), any(String.class));
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
