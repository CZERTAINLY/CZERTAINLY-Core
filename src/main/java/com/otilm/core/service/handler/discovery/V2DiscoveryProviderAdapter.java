package com.otilm.core.service.handler.discovery;

import com.otilm.api.model.client.discovery.DiscoveryDetailDto;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.exception.UnsupportedDiscoveryVersionException;
import com.otilm.core.tasks.ScheduledJobInfo;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Placeholder v2 adapter: the factory can route to it, but no create path writes a v2 interface association yet.
 * {@code start} refuses with {@link UnsupportedDiscoveryVersionException} so a run that gains the association anyway
 * ends terminal FAILED through the dispatch-refusal path instead of being left non-terminal by the async caller; the
 * synchronous operations fail loud with {@link IllegalStateException}.
 */
@Component
public class V2DiscoveryProviderAdapter implements DiscoveryProviderAdapter {

    private static final String NOT_IMPLEMENTED = "The discovery v2 provider adapter is not implemented yet";

    @Override
    public DiscoveryDetailDto start(UUID discoveryUuid, ScheduledJobInfo scheduledJobInfo) {
        throw new UnsupportedDiscoveryVersionException(NOT_IMPLEMENTED);
    }

    @Override
    public void stop(Discovery discovery) {
        throw new IllegalStateException(NOT_IMPLEMENTED);
    }

    @Override
    public void resume(Discovery discovery) {
        throw new IllegalStateException(NOT_IMPLEMENTED);
    }

    @Override
    public void cancel(Discovery discovery) {
        throw new IllegalStateException(NOT_IMPLEMENTED);
    }
}
