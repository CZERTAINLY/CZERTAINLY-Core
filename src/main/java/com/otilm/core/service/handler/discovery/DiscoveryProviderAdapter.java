package com.otilm.core.service.handler.discovery;

import com.otilm.api.model.client.discovery.DiscoveryDetailDto;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.tasks.ScheduledJobInfo;
import java.util.UUID;

/**
 * The version seam for discovery provider interaction: one implementation per connector discovery interface generation,
 * selected by {@link DiscoveryProviderAdapterFactory}.
 *
 * <p>
 * {@code start} takes the run's uuid rather than the entity: the v1 flow reloads the run with its associations inside
 * its own transaction boundaries, because it executes asynchronously in a different session than the one that created
 * the run.
 */
public interface DiscoveryProviderAdapter {

    /** Runs the whole provider-side discovery for the run, returning the run detail at handoff or completion. */
    DiscoveryDetailDto start(UUID discoveryUuid, ScheduledJobInfo scheduledJobInfo);

    /** @throws UnsupportedOperationException when the connector version cannot suspend a run (v1 cannot). */
    void stop(Discovery discovery);

    /** @throws UnsupportedOperationException when the connector version cannot resume a run (v1 cannot). */
    void resume(Discovery discovery);

    /** @throws UnsupportedOperationException when the connector version cannot cancel a run (v1 cannot). */
    void cancel(Discovery discovery);
}
