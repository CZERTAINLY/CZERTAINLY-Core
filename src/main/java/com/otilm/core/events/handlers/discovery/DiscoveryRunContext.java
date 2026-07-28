package com.otilm.core.events.handlers.discovery;

import com.otilm.core.dao.entity.Certificate;
import com.otilm.core.dao.entity.workflows.TriggerAssociation;
import com.otilm.core.events.EventContext;

import java.util.List;
import java.util.UUID;

/**
 * Values that are invariant for a whole discovery run.
 *
 * <p>Deliberately carries the discovery's identifiers and immutable fields rather than the
 * {@code DiscoveryHistory} entity. Sharing one detached instance across worker threads and saving it from each
 * corrupts progress reporting and rolls back committed certificate work, so the entity stays out of this record.
 * {@code eventContext} is a per-run holder for the trigger evaluator and event data, not a persistent entity, so
 * sharing it is safe.
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
                                  int totalGroups,
                                  EventContext<Certificate> eventContext) {
}
