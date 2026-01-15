package com.czertainly.core.messaging.proxy;

import com.czertainly.api.clients.mq.model.ProxyMessage;
import com.czertainly.api.exception.MessageHandlingException;
import com.czertainly.core.messaging.jms.listeners.MessageProcessor;
import com.czertainly.core.messaging.proxy.handler.MessageTypeHandlerRegistry;
import com.czertainly.core.messaging.proxy.redis.RedisResponseDistributor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Listener that processes proxy messages.
 * Implements MessageProcessor to integrate with the existing JMS listener infrastructure.
 *
 * <p>Message routing decision tree:</p>
 * <ol>
 *   <li>If a handler is registered for the messageType → dispatch to handler (fire-and-forget)</li>
 *   <li>If correlation ID found in local correlator → complete the pending request</li>
 *   <li>Otherwise → distribute via Redis for other instances to handle</li>
 * </ol>
 */
@Slf4j
@Component
public class ProxyMessageListener implements MessageProcessor<ProxyMessage> {

    private final ProxyMessageCorrelator correlator;
    private final MessageTypeHandlerRegistry handlerRegistry;
    private final RedisResponseDistributor redisDistributor;

    public ProxyMessageListener(
            ProxyMessageCorrelator correlator,
            @Autowired(required = false) MessageTypeHandlerRegistry handlerRegistry,
            @Autowired(required = false) RedisResponseDistributor redisDistributor) {
        this.correlator = correlator;
        this.handlerRegistry = handlerRegistry;
        this.redisDistributor = redisDistributor;
        log.info("ProxyMessageListener initialized (handlerRegistry={}, redisDistributor={})",
                handlerRegistry != null ? "enabled" : "disabled",
                redisDistributor != null ? "enabled" : "disabled");
    }

    @Override
    public void processMessage(ProxyMessage message) throws MessageHandlingException {
        if (message == null) {
            log.warn("Received null proxy message, ignoring");
            return;
        }

        String correlationId = message.getCorrelationId();
        String messageType = message.getMessageType();

        int statusCode = message.hasConnectorResponse() ? message.getConnectorResponse().getStatusCode() : 0;
        boolean hasError = message.hasError();
        log.debug("Processing proxy message correlationId={} messageType={} proxyId={} statusCode={} hasError={}",
                correlationId, messageType, message.getProxyId(), statusCode, hasError);

        try {
            // 1. Check if handler registered for this messageType (fire-and-forget pattern)
            if (messageType != null && handlerRegistry != null && handlerRegistry.hasHandler(messageType)) {
                log.debug("Dispatching to messageType handler: messageType={}", messageType);
                handlerRegistry.dispatch(message);
                return; // Any instance can handle - done
            }

            // 2. No handler - correlation-based routing
            // Validate correlation ID for correlation-based routing
            if (correlationId == null || correlationId.isBlank()) {
                log.warn("Received proxy message without correlationId and no messageType handler, ignoring. " +
                        "StatusCode: {}, messageType: {}", statusCode, messageType);
                return;
            }

            // Try local correlator first
            if (correlator.tryCompleteRequest(message)) {
                log.debug("Completed request from local correlator: correlationId={}", correlationId);
                return; // Handled locally
            }

            // 3. Not found locally - distribute via Redis for other instances
            if (redisDistributor != null) {
                log.debug("Distributing message via Redis: correlationId={}", correlationId);
                redisDistributor.publishResponse(message);
            } else {
                // No Redis - this response may be lost if the owning instance is different
                log.warn("Message not handled locally and Redis distribution not available: correlationId={}",
                        correlationId);
            }

        } catch (Exception e) {
            log.error("Failed to process proxy message correlationId={}: {}",
                    correlationId, e.getMessage(), e);
            throw new MessageHandlingException("Failed to process proxy message: " + e.getMessage(), e);
        }
    }
}
