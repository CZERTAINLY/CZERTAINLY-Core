package com.otilm.core.events.handlers.discovery;

import com.otilm.core.dao.entity.DiscoveryHistory;

import java.util.UUID;

/**
 * The discovery's identity, as recorded against a certificate's metadata and event history.
 *
 * <p>Exists so neither the download phase nor post-processing has to pass the {@code DiscoveryHistory} entity
 * itself. One detached instance saved from parallel workers corrupts progress reporting, so the entity must not
 * cross into either phase.
 */
public record DiscoverySource(UUID discoveryUuid,
                              String discoveryName,
                              UUID connectorUuid,
                              String connectorName,
                              String discoveryKind) {

    public static DiscoverySource of(DiscoveryRunContext context) {
        return new DiscoverySource(context.discoveryUuid(), context.discoveryName(), context.connectorUuid(),
                context.connectorName(), context.discoveryKind());
    }

    public static DiscoverySource of(DiscoveryHistory discovery) {
        return new DiscoverySource(discovery.getUuid(), discovery.getName(), discovery.getConnectorUuid(),
                discovery.getConnectorName(), discovery.getKind());
    }
}
