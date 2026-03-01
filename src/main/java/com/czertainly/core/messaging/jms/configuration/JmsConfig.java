package com.czertainly.core.messaging.jms.configuration;

import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.core.credential.TokenCredential;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.jms.ConnectionFactory;
import org.apache.qpid.jms.JmsConnectionExtensions;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.slf4j.Logger;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;
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
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(props.getEffectiveBrokerUrl());

        // For RabbitMQ with AMQP 1.0, vhost is specified in the AMQP Open frame hostname field
        // The hostname field must be "vhost:name" format according to RabbitMQ AMQP 1.0 docs
        // We use amqp.vhost connection property to set this value
        if (props.brokerType() == MessagingProperties.BrokerType.RABBITMQ &&
                props.virtualHost() != null && !props.virtualHost().isEmpty()) {
            builder.queryParam("amqp.vhost", "vhost:" + props.virtualHost());
        }

        // AMQP idle timeout: tells the remote peer how often to send heartbeat (empty) frames
        // to keep the connection alive at the transport level (default: 2 minutes).
        // See: http://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-transport-v1.0-os.html#doc-doc-idle-time-out
        int amqpIdleTimeout = props.amqpIdleTimeout() != null ? props.amqpIdleTimeout() : 120000;
        if (amqpIdleTimeout > 0) {
            builder.queryParam("amqp.idleTimeout", amqpIdleTimeout);
        }

        String brokerUrl = builder.build().toUriString();

        JmsConnectionFactory factory = new JmsConnectionFactory(brokerUrl);
        factory.setForceSyncSend(true);

        if (props.brokerType() == MessagingProperties.BrokerType.SERVICEBUS) {
            configureServiceBusAuthentication(factory, props);
            // Return raw factory for ServiceBus - listener containers manage their own connections.
            // CachingConnectionFactory interferes with DefaultMessageListenerContainer recovery
            // for durable/shared subscriptions. Producers use JmsPoolConnectionFactory (see below).
            return factory;
        }

        // RabbitMQ - standard username/password authentication
        logger.info("Connecting to RabbitMQ broker: {} with vhost: {}", props.getEffectiveBrokerUrl(), props.virtualHost());
        factory.setUsername(props.username());
        factory.setPassword(props.password());

        return factory;
    }

    private void configureServiceBusAuthentication(JmsConnectionFactory factory, MessagingProperties props) {
        if (props.aadAuth() != null && props.aadAuth().isEnabled()) {
            // AAD (Entra ID) OAuth2 authentication
            logger.info("Connecting to ServiceBus: {} using AAD authentication", props.getEffectiveBrokerUrl());

            TokenCredential credential = new ClientSecretCredentialBuilder()
                    .tenantId(props.aadAuth().tenantId())
                    .clientId(props.aadAuth().clientId())
                    .clientSecret(props.aadAuth().clientSecret())
                    .build();

            AadTokenProvider tokenProvider = new AadTokenProvider(credential, props.aadAuth().tokenRefreshInterval(), props.aadAuth().tokenGettingTimeout());

            // ServiceBus requires "$jwt" username for OAuth2 token authentication
            factory.setUsername("$jwt");
            factory.setExtension(
                    JmsConnectionExtensions.PASSWORD_OVERRIDE.toString(),
                    tokenProvider
            );
        } else {
            // SAS (Shared Access Signature) token authentication
            logger.info("Connecting to Azure ServiceBus: {} using SAS authentication", props.getEffectiveBrokerUrl());
            factory.setUsername(props.username());
            factory.setPassword(props.password());
        }
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

        // Configure ObjectMapper with Java 8 date/time support
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        converter.setObjectMapper(objectMapper);

        return converter;
    }

    @Bean(destroyMethod = "stop")
    public JmsPoolConnectionFactory producerConnectionFactory(ConnectionFactory connectionFactory,
                                                               MessagingProperties messagingProperties) {
        // JmsPoolConnectionFactory manages connection/session lifecycle independently of
        // Spring's shared-connection mechanism. On connection failure (e.g. amqp:connection:forced),
        // the pool auto-evicts the dead connection and provides a fresh one on the next borrow —
        // no manual resetConnection() needed.
        //
        // connectionIdleTimeout=30s evicts idle connections before the broker's idle thresholds.
        // useAnonymousProducers=true keeps a persistent AMQP link on the connection, preventing
        // the broker's "no active links" forced close.
        // connectionCheckInterval=60s enables a background thread to actively evict stale connections.
        MessagingProperties.Pool poolConfig = messagingProperties.pool();
        if (poolConfig == null) {
            poolConfig = new MessagingProperties.Pool(null, null, null, null, null);
        }

        JmsPoolConnectionFactory pool = new JmsPoolConnectionFactory();
        pool.setConnectionFactory(connectionFactory);
        pool.setMaxConnections(poolConfig.maxConnections());
        pool.setConnectionIdleTimeout(poolConfig.connectionIdleTimeout());
        pool.setConnectionCheckInterval(poolConfig.connectionCheckInterval());
        pool.setMaxSessionsPerConnection(poolConfig.maxSessionsPerConnection());
        pool.setUseAnonymousProducers(poolConfig.useAnonymousProducers());
        pool.start();
        logger.info("Started JMS producer connection pool: maxConnections={}, connectionIdleTimeout={}ms, connectionCheckInterval={}ms, maxSessionsPerConnection={}, useAnonymousProducers={}",
                poolConfig.maxConnections(), poolConfig.connectionIdleTimeout(),
                poolConfig.connectionCheckInterval(), poolConfig.maxSessionsPerConnection(),
                poolConfig.useAnonymousProducers());
        return pool;
    }

    @Bean
    public JmsTemplate jmsTemplate(JmsPoolConnectionFactory producerConnectionFactory,
                                   MessageConverter messageConverter,
                                   MessagingProperties messagingProperties) {
        JmsTemplate template = new JmsTemplate(producerConnectionFactory);
        template.setMessageConverter(messageConverter);
        if (messagingProperties.brokerType() == MessagingProperties.BrokerType.SERVICEBUS) {
            template.setPubSubDomain(true);
        }
        return template;
    }
}