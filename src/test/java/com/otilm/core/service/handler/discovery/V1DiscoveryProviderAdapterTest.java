package com.otilm.core.service.handler.discovery;

import com.otilm.core.dao.entity.Discovery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pins that the v1 interface rejects the lifecycle operations it never supported. */
class V1DiscoveryProviderAdapterTest {

    // The rejecting operations touch no collaborator, so the adapter is constructed without any.
    private final V1DiscoveryProviderAdapter adapter = new V1DiscoveryProviderAdapter(null, null, null, null, null,
            null, null, null, null, null, null, null);

    @Test
    void lifecycleOperationsAreUnsupported() {
        Discovery run = new Discovery();

        assertThatThrownBy(() -> adapter.stop(run)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> adapter.resume(run)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> adapter.cancel(run)).isInstanceOf(UnsupportedOperationException.class);
    }
}
