package com.otilm.core.events.handlers.discovery;

import com.otilm.core.dao.entity.Certificate;
import com.otilm.core.dao.entity.workflows.TriggerAssociation;
import com.otilm.core.events.EventContext;

import java.util.List;
import java.util.UUID;

/**
 * Values that are invariant for a whole discovery run.
 *
 * <p>
 * Deliberately carries the discovery's identifiers and immutable fields rather than the {@code Discovery} entity — see
 * {@link DiscoverySource} for why. {@code eventContext} is a per-run holder for the trigger evaluator and event data,
 * not a persistent entity, so sharing it is safe.
 *
 * @param totalGroups how many content groups the whole run has, which is what the import's progress percentage is a
 * fraction of — or {@code null} when the caller sees only part of the run and reports its own progress. The v1 pass
 * imports a run in one go and knows the total; a v2 processing tick sees one bounded batch, and counting that batch as
 * the whole would report every batch as 100%.
 */
public record DiscoveryRunContext(UUID discoveryUuid, String discoveryName, UUID connectorUuid, String connectorName,
        String discoveryKind, UUID userUuid, List<TriggerAssociation> ignoreTriggers, List<TriggerAssociation> triggers,
        UUID discoveryEventHistoryUuid, UUID platformEventHistoryUuid, Integer totalGroups,
        EventContext<Certificate> eventContext) {
}
