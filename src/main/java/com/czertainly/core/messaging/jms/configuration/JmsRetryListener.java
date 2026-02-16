package com.czertainly.core.messaging.jms.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

/**
 * Retry listener for JMS operations that provides logging of retry attempts and failures.
 *
 * <p>This listener logs:
 * <ul>
 *   <li>WARN level: Each retry attempt failure (transient errors)</li>
 *   <li>ERROR level: Final failure after all retries exhausted</li>
 * </ul>
 *
 * <p>Thread-safety: This class is stateless and safe to use as a singleton
 * registered in {@link org.springframework.retry.support.RetryTemplate}.</p>
 *
 * @see RetryConfig#jmsRetryTemplate(MessagingProperties)
 * @see com.czertainly.core.messaging.jms.listeners.AbstractJmsEndpointConfig
 */
public class JmsRetryListener implements RetryListener {
    private static final Logger logger = LoggerFactory.getLogger(JmsRetryListener.class);
    public static final String ENDPOINT_ID_ATTR = "endpointId";

    @Override
    public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
        String endpointId = getEndpointId(context);
        if (endpointId != null) {
            logger.debug("Starting retry operation for endpoint '{}'", endpointId);
        }
        return true;
    }

    @Override
    public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        if (throwable != null) {
            String endpointId = getEndpointId(context);
            if (endpointId != null) {
                logger.error("Failed to process message in endpoint '{}' (messageId={}, type={}) after {} attempts",
                        endpointId,
                        context.getAttribute("messageId"),
                        context.getAttribute("messageClass"),
                        context.getRetryCount(),
                        throwable);
            } else {
                // Fallback for producer - does not have endpointId
                logger.error("Failed to process message for (messageId={}, type={}) after {} attempts",
                        context.getAttribute("messageId"),
                        context.getAttribute("messageClass"),
                        context.getRetryCount(),
                        throwable);
            }
        }
    }

    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        String endpointId = getEndpointId(context);
        if (endpointId != null) {
            logger.warn("Retry attempt {} failed in endpoint '{}': {}",
                    context.getRetryCount(),
                    endpointId,
                    throwable.getMessage(),
                    throwable);
        } else {
            // Fallback for producer
            logger.warn("Retry attempt {} failed for JMS message: {}",
                    context.getRetryCount(),
                    throwable.getMessage());
        }
    }

    private String getEndpointId(RetryContext context) {
        Object endpointId = context.getAttribute(ENDPOINT_ID_ATTR);
        return endpointId != null ? endpointId.toString() : null;
    }
}