package com.otilm.core.events.handlers.discovery;

import com.otilm.core.dao.entity.workflows.TriggerAssociation;

import java.util.List;
import java.util.UUID;

/**
 * Values that are invariant for a whole discovery run.
 *
 * <p>Deliberately carries identifiers and immutable values rather than the {@code DiscoveryHistory} entity:
 * sharing one detached instance across worker threads, and saving it from each, is what corrupted progress
 * reporting and rolled back committed certificate work.
 */
public record DiscoveryRunContext(UUID discoveryUuid,
                                  String discoveryName,
                                  UUID connectorUuid,
                                  String connectorName,
                                  String discoveryKind,
                                  UUID userUuid,
                                  List<TriggerAssociation> ignoreTriggers,
                                  List<TriggerAssociation> triggers,
                                  UUID discoveryEventHistoryUuid,
                                  UUID platformEventHistoryUuid,
                                  int totalGroups) {
}
