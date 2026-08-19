package com.otilm.core.service.handler.discovery;

import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.exception.UnsupportedDiscoveryVersionException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pins the placeholder's failure modes until the real v2 implementation replaces it. */
class V2DiscoveryProviderAdapterTest {

    private final V2DiscoveryProviderAdapter adapter = new V2DiscoveryProviderAdapter();

    private final Discovery run = new Discovery();

    @Test
    void startRefusesAsUnsupportedSoTheRunEndsTerminal() {
        UUID discoveryUuid = UUID.randomUUID();

        assertThatThrownBy(() -> adapter.start(discoveryUuid, null))
                .isInstanceOf(UnsupportedDiscoveryVersionException.class)
                .hasMessageContaining("not implemented");
    }

    @Test
    void stopFailsLoud() {
        assertThatThrownBy(() -> adapter.stop(run)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resumeFailsLoud() {
        assertThatThrownBy(() -> adapter.resume(run)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelFailsLoud() {
        assertThatThrownBy(() -> adapter.cancel(run)).isInstanceOf(IllegalStateException.class);
    }
}
