package com.otilm.core.service.handler.discovery;

import com.otilm.api.model.client.discovery.DiscoveryDetailDto;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.tasks.ScheduledJobInfo;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Placeholder the factory can already route to: a run associated with a v2 discovery interface exists only once the v2
 * create path writes the association, and that path arrives together with the real implementation of this class.
 */
@Component
public class V2DiscoveryProviderAdapter implements DiscoveryProviderAdapter {

    private static final String NOT_IMPLEMENTED = "The discovery v2 provider adapter is not implemented yet";

    @Override
    public DiscoveryDetailDto start(UUID discoveryUuid, ScheduledJobInfo scheduledJobInfo) {
        throw new IllegalStateException(NOT_IMPLEMENTED);
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
