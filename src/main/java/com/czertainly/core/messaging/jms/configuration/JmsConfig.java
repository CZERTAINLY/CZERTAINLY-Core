package com.czertainly.core.messaging.jms.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.ConnectionFactory;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.slf4j.Logger;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;
import org.springframework.web.util.UriComponentsBuilder;

import static org.slf4j.LoggerFactory.getLogger;

@EnableJms
@Configuration
@EnableConfigurationProperties({MessagingProperties.class, MessagingConcurrencyProperties.class})
public class JmsConfig {
    private static final Logger logger = getLogger(JmsConfig.class);

    @Bean
    public ConnectionFactory connectionFactory(MessagingProperties props) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(props.brokerUrl());

        // For RabbitMQ with AMQP 1.0, vhost is specified in the AMQP Open frame hostname field
        // The hostname field must be "vhost:name" format according to RabbitMQ AMQP 1.0 docs
        // We use amqp.vhost connection property to set this value
        if (props.brokerType() == MessagingProperties.BrokerType.RABBITMQ &&
                props.vhost() != null && !props.vhost().isEmpty()) {
            builder.queryParam("amqp.vhost", "vhost:" + props.vhost());
        }
        String brokerUrl = builder.build().toUriString();

        logger.info("Connecting to broker: {} with vhost: {}", props.brokerUrl(), props.vhost());

        JmsConnectionFactory factory = new JmsConnectionFactory(brokerUrl);
        factory.setUsername(props.user());
        factory.setPassword(props.password());
        factory.setForceSyncSend(true);
        if (props.brokerType() == MessagingProperties.BrokerType.SERVICEBUS) {
            return factory;
        }

        // caching connection favtory for non-ServiceBus (RabbitMQ)
        CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory(factory);
        cachingConnectionFactory.setSessionCacheSize(props.sessionCacheSize());
        cachingConnectionFactory.setReconnectOnException(true);

        return cachingConnectionFactory;
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            MessagingProperties messagingProperties) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        if (messagingProperties.brokerType() == MessagingProperties.BrokerType.SERVICEBUS) {
            factory.setPubSubDomain(true);
            factory.setSubscriptionDurable(true);
        }
        if (messagingProperties.listener() != null && messagingProperties.listener().recoveryInterval() != null) {
            factory.setRecoveryInterval(messagingProperties.listener().recoveryInterval());
        }
        return factory;
    }

    @Bean
    public MessageConverter messageConverter(ObjectMapper jacksonObjectMapper) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(jacksonObjectMapper);
        converter.setTargetType(MessageType.TEXT);
        return converter;
    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory,
                                   MessageConverter messageConverter,
                                   MessagingProperties messagingProperties) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        if (messagingProperties.brokerType() == MessagingProperties.BrokerType.SERVICEBUS) {
            template.setPubSubDomain(true);
        }
        return template;
    }
}