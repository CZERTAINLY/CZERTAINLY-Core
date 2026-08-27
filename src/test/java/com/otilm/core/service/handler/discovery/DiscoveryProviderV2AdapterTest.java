package com.otilm.core.service.handler.discovery;

import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.dao.entity.Discovery;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The operation-legality matrix, which is what stands between a client and a run in a state that cannot honour the
 * request. Refusals happen before any collaborator is touched, so the adapter is built with none — a mock here would
 * assert nothing, and would hide it if a refusal ever started reaching the connector first.
 *
 * <p>
 * The happy paths need a connector and live in {@code DiscoveryServiceITest}.
 */
class DiscoveryProviderV2AdapterTest {

    private final DiscoveryProviderV2Adapter adapter = new DiscoveryProviderV2Adapter(null, null, null, null, null,
            null, null, null, null);

    @ParameterizedTest
    @EnumSource(value = DiscoveryStatus.class, names = "IN_PROGRESS", mode = EnumSource.Mode.EXCLUDE)
    void stopIsRefusedUnlessTheRunIsInProgress(DiscoveryStatus status) {
        assertThatThrownBy(() -> adapter.stop(runWith(status)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be stopped");
    }

    @ParameterizedTest
    @EnumSource(value = DiscoveryStatus.class, names = "STOPPED", mode = EnumSource.Mode.EXCLUDE)
    void resumeIsRefusedUnlessTheRunIsStopped(DiscoveryStatus status) {
        assertThatThrownBy(() -> adapter.resume(runWith(status)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be resumed");
    }

    @ParameterizedTest
    @EnumSource(value = DiscoveryStatus.class, names = {"IN_PROGRESS", "STOPPED"}, mode = EnumSource.Mode.EXCLUDE)
    void cancelIsRefusedOnceTheRunIsPastDriving(DiscoveryStatus status) {
        assertThatThrownBy(() -> adapter.cancel(runWith(status)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be cancelled");
    }

    @Test
    void aRunWithNoStatusAtAllIsRefusedRatherThanAssumed() {
        // Null is not a legal state for any operation, and defaulting it either way would drive a run whose status
        // could not be read.
        assertThatThrownBy(() -> adapter.cancel(runWith(null))).isInstanceOf(ValidationException.class);
    }

    private static Discovery runWith(DiscoveryStatus status) {
        Discovery run = new Discovery();
        run.setUuid(UUID.randomUUID());
        run.setStatus(status);
        return run;
    }
}
