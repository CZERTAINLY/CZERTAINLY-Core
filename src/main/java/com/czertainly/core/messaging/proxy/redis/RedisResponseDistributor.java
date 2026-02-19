package com.czertainly.core.messaging.proxy.redis;

import com.czertainly.api.clients.mq.model.ProxyMessage;
import com.czertainly.core.messaging.proxy.ProxyProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes proxy messages to Redis pub/sub channel for distribution to other instances.
 *
 * <p>When a Core instance receives a proxy message via JMS but doesn't have a matching
 * pending request (correlation ID not found locally), it publishes the message to Redis.
 * Other instances subscribed to the same channel can then complete their pending requests.</p>
 */
@Slf4j
@Component
@Profile("!test")
@ConditionalOnProperty(prefix = "proxy.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisResponseDistributor {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String channel;

    public RedisResponseDistributor(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ProxyProperties proxyProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.channel = proxyProperties.redis().channel();
        log.info("RedisResponseDistributor initialized with channel: {}", channel);
    }

    /**
     * Publish a proxy message to Redis for distribution to other instances.
     *
     * @param message The proxy message to publish
     */
    public void publishResponse(ProxyMessage message) {
        if (message == null) {
            log.warn("Attempted to publish null proxy message to Redis, ignoring");
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, json);
            log.debug("Published proxy message to Redis channel={} correlationId={}",
                    channel, message.getCorrelationId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize proxy message for Redis distribution: correlationId={}, error={}",
                    message.getCorrelationId(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to publish proxy message to Redis: channel={}, correlationId={}, error={}",
                    channel, message.getCorrelationId(), e.getMessage(), e);
        }
    }
}
