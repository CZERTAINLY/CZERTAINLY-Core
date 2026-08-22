package com.otilm.core.messaging.jms.listeners.discovery;

import com.otilm.core.messaging.model.DiscoveryWorkMessage;
import com.otilm.core.model.discovery.DiscoveryWorkType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DiscoveryWorkListenerTest {

    @Test
    void processMessage_acknowledgesWithoutDispatch() {
        DiscoveryWorkMessage message = new DiscoveryWorkMessage(UUID.randomUUID(), DiscoveryWorkType.STATUS, 1);

        // The listener must log and acknowledge a message it has no handler for, never throw:
        // a throw would send an undeliverable message through the broker's redelivery loop.
        assertDoesNotThrow(() -> new DiscoveryWorkListener().processMessage(message));
    }
}
