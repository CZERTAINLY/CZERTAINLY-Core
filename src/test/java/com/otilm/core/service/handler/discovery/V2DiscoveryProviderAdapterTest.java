package com.otilm.core.service.handler.discovery;

import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.exception.UnsupportedDiscoveryVersionException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pins the placeholder's failure modes until the real v2 implementation replaces it. */
class V2DiscoveryProviderAdapterTest {

    private final V2DiscoveryProviderAdapter adapter = new V2DiscoveryProviderAdapter();

    @Test
    void startRefusesAsUnsupportedSoTheRunEndsTerminal() {
        assertThatThrownBy(() -> adapter.start(UUID.randomUUID(), null))
                .isInstanceOf(UnsupportedDiscoveryVersionException.class)
                .hasMessageContaining("not implemented");
    }

    @Test
    void synchronousOperationsFailLoud() {
        Discovery run = new Discovery();

        assertThatThrownBy(() -> adapter.stop(run)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.resume(run)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.cancel(run)).isInstanceOf(IllegalStateException.class);
    }
}
