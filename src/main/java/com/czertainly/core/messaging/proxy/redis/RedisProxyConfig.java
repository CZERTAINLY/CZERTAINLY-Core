package com.czertainly.core.messaging.proxy.redis;

import com.czertainly.core.messaging.proxy.ProxyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Redis configuration for distributed proxy response coordination.
 *
 * <p>When multiple Core instances share the same JMS subscription, responses may be
 * consumed by instances that don't have the corresponding pending request. This configuration
 * sets up Redis pub/sub to distribute such responses to all instances, allowing the correct
 * instance to complete the pending request.</p>
 */
@Slf4j
@Configuration
@Profile("!test")
@ConditionalOnProperty(prefix = "proxy.redis", name = "enabled", havingValue = "true")
public class RedisProxyConfig {

    private final ProxyProperties proxyProperties;

    public RedisProxyConfig(ProxyProperties proxyProperties) {
        this.proxyProperties = proxyProperties;

        // TODO: Redis module will be removed in Task 5
        log.info("Initializing Redis proxy config");
    }

    /**
     * Redis pub/sub topic for proxy response distribution.
     */
    @Bean
    public ChannelTopic proxyResponseTopic() {
        // TODO: Redis module will be removed in Task 5
        return new ChannelTopic("proxy:responses");
    }

    /**
     * Message listener adapter wrapping the RedisResponseSubscriber.
     */
    @Bean
    public MessageListenerAdapter proxyResponseListenerAdapter(RedisResponseSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    /**
     * Redis message listener container for proxy responses.
     */
    @Bean
    public RedisMessageListenerContainer proxyResponseListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter proxyResponseListenerAdapter,
            ChannelTopic proxyResponseTopic) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(proxyResponseListenerAdapter, proxyResponseTopic);

        log.info("Redis proxy response listener configured on channel: {}", proxyResponseTopic.getTopic());
        return container;
    }
}
