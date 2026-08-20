package com.otilm.core.messaging.jms.listeners.discovery;

import com.otilm.core.messaging.jms.listeners.MessageProcessor;
import com.otilm.core.messaging.model.DiscoveryWorkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code provider.discovery-work} tick messages. No discovery-work handler is configured in this build —
 * nothing schedules {@code discovery_work} rows, so the queue is unfed and any message here is unexpected: logged and
 * acknowledged, since redelivering it could achieve nothing.
 */
@Component
public class DiscoveryWorkListener implements MessageProcessor<DiscoveryWorkMessage> {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryWorkListener.class);

    @Override
    public void processMessage(DiscoveryWorkMessage message) {
        logger
                .warn("Discarding discovery work tick (run {}, type {}, attempt {}): no discovery-work handler is "
                        + "configured", message.discoveryUuid(), message.workType(), message.attempt());
    }
}
