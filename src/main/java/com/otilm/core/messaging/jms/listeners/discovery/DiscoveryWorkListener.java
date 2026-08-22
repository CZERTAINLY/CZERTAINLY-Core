package com.otilm.core.messaging.jms.listeners.discovery;

import com.otilm.core.messaging.jms.listeners.MessageProcessor;
import com.otilm.core.messaging.model.DiscoveryWorkMessage;
import com.otilm.core.service.handler.discovery.DiscoveryStatusTickWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code provider.discovery-work} ticks and hands each to the worker for its type.
 *
 * <p>
 * <b>No {@code @Transactional} here:</b> a tick calls the connector, and a connector call must never run inside a
 * transaction or hold a row lock. Each worker opens its own short transactions around the writes its answer justifies.
 *
 * <p>
 * <b>A failed tick is acknowledged, not redelivered.</b> Retry is the {@code discovery_work} agenda's job: the row was
 * already pushed up its backoff ladder when the tick was claimed, so the sweep re-publishes it when it next comes due.
 * Letting the exception reach the broker would add a second, uncoordinated retry loop on top of that one, at the
 * broker's cadence rather than the run's.
 */
@Component
public class DiscoveryWorkListener implements MessageProcessor<DiscoveryWorkMessage> {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryWorkListener.class);

    private final DiscoveryStatusTickWorker statusWorker;

    public DiscoveryWorkListener(DiscoveryStatusTickWorker statusWorker) {
        this.statusWorker = statusWorker;
    }

    @Override
    public void processMessage(DiscoveryWorkMessage message) {
        try {
            switch (message.workType()) {
                case STATUS -> statusWorker.tick(message.discoveryUuid(), message.attempt());
                case DRAIN,
                        PROCESS ->
                    logger
                            .warn("Discarding {} tick for discovery {}: no worker is wired for it yet",
                                    message.workType(), message.discoveryUuid());
            }
        } catch (RuntimeException e) {
            logger
                    .error("Discovery {} tick failed for run {} (attempt {}); the agenda row retries when next due",
                            message.workType(), message.discoveryUuid(), message.attempt(), e);
        }
    }
}
