package com.otilm.core.service.handler.discovery;

import com.otilm.core.dao.entity.Discovery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the lifecycle operations that are still placeholders. {@code start} is implemented and covered by
 * {@code DiscoveryServiceITest}, which drives it against a connector rather than around one.
 *
 * <p>
 * Collaborators are null on purpose: each method below refuses before touching one, so a mock would assert nothing and
 * would hide it if that stopped being true.
 */
class DiscoveryProviderV2AdapterTest {

    private final DiscoveryProviderV2Adapter adapter = new DiscoveryProviderV2Adapter(null, null, null, null, null,
            null, null, null, null);

    private final Discovery run = new Discovery();

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
