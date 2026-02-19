package com.czertainly.core.messaging.jms.listeners;

import com.czertainly.core.messaging.jms.configuration.JmsRetryListener;
import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.JmsException;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.MessageListenerContainer;
import org.springframework.messaging.MessagingException;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;
import java.util.function.Supplier;

import static org.slf4j.LoggerFactory.getLogger;

public abstract class AbstractJmsEndpointConfig<T> {

    private static final Logger logger = getLogger(AbstractJmsEndpointConfig.class);
    private static final String ROUTING_KEY = "routingKey";

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    protected MessageProcessor<T> listenerMessageProcessor;
    @Autowired
    protected RetryTemplate jmsRetryTemplate;
    @Autowired
    protected MessagingProperties messagingProperties;

    public abstract SimpleJmsListenerEndpoint listenerEndpoint();

    /**
     *
     * @param endpointId unique id for the endpoint
     * @param destination or Topic name in Azure ServiceBus
     * @param routingKey or Subscription name in Azure ServiceBus
     * @param concurrency number of threads
     * @param messageClass type of message to be processed
     * @return endpoint to register in Spring context
     */
    public SimpleJmsListenerEndpoint listenerEndpointInternal(Supplier<String> endpointId, Supplier<String> destination,
                                                              Supplier<String> routingKey, Supplier<String> concurrency,
                                                              Class<T> messageClass) {
        logger.debug("Configuring JMS listener endpoint: id={}, destination={}, routingKey={}, broker={}, vhost={}",
            endpointId.get(), destination.get(), routingKey.get(), messagingProperties.brokerType(), messagingProperties.virtualHost());

        SimpleJmsListenerEndpoint endpoint;
        if (messagingProperties.brokerType() == MessagingProperties.BrokerType.SERVICEBUS) {
            endpoint = new SimpleJmsListenerEndpoint() {
                @Override
                public void setupListenerContainer(MessageListenerContainer listenerContainer) {
                    super.setupListenerContainer(listenerContainer);
                    if (listenerContainer instanceof DefaultMessageListenerContainer container) {
                        container.setSubscriptionShared(true);// Shared must be set to allow concurrency
                        container.setSubscriptionDurable(true);
                        container.setDurableSubscriptionName(routingKey.get());
                    }
                }
            };
        } else {
            endpoint = new SimpleJmsListenerEndpoint();
        }

        endpoint.setId(endpointId.get());

        if (messagingProperties.brokerType() == MessagingProperties.BrokerType.SERVICEBUS) {
            endpoint.setSubscription(routingKey.get());
            endpoint.setSelector(ROUTING_KEY + " = '" + routingKey.get() + "'");
        }

        endpoint.setDestination(destination.get());
        endpoint.setConcurrency(concurrency.get());

        endpoint.setMessageListener(jmsMessage -> {
            logger.debug(">>> RECEIVED MESSAGE in endpoint: {}", endpointId.get());
            jmsRetryTemplate.execute(context -> {
                try {
                    context.setAttribute(JmsRetryListener.ENDPOINT_ID_ATTR, endpointId.get());
                    context.setAttribute("messageId", jmsMessage.getJMSMessageID());
                    context.setAttribute("messageClass", messageClass.getSimpleName());

                    String json = extractMessageText(jmsMessage, endpointId.get());
                    logger.debug("Message JSON in endpoint {}: {}", endpointId.get(), json);
                    T message = objectMapper.readValue(json, messageClass);
                    listenerMessageProcessor.processMessage(message);
                } catch (JmsException | IOException e) {
                    // Retryable - network, broker issues
                    throw new MessagingException("Message processing failed in endpoint: " + endpointId.get(), e);
                } catch (IllegalArgumentException e) {
                    // Non-retryable - bad message format, validation
                    logger.error("Invalid message in endpoint '{}', will not retry: {}", endpointId.get(), e.getMessage());
                    throw e; // Don't wrap, don't retry
                } catch (Exception e) {
                    logger.error("Unexpected error in endpoint '{}'", endpointId.get(), e);
                }

                return null;
            });
        });

        return endpoint;
    }

    private String extractMessageText(jakarta.jms.Message jmsMessage, String endpointId) throws JMSException {
        if (!(jmsMessage instanceof TextMessage textMessage)) {
            throw new IllegalArgumentException("Expected TextMessage in endpoint '" + endpointId +
                    "' but got: " + (jmsMessage != null ? jmsMessage.getClass().getName() : "null"));
        }
        String text = textMessage.getText();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Received empty message body in endpoint '" + endpointId + "'");
        }
        return text;
    }
}
