package com.otilm.core.service.handler.discovery;

import com.otilm.core.dao.entity.ConnectorInterfaceEntity;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.repository.ConnectorInterfaceRepository;
import com.otilm.core.exception.UnsupportedDiscoveryVersionException;
import org.springframework.stereotype.Component;

/**
 * Dispatches discovery operations to the adapter matching the run's connector interface version.
 *
 * <p>
 * A run with no connector-interface association is a v1 run — the deliberate inverse of
 * {@code AuthorityProviderAdapterFactory}'s NULL rule, because discovery's legacy is the v1 interface: every run
 * created before the v2 create path exists carries no association, and the v1 adapter also owns the legacy failure
 * behavior for runs that cannot be loaded at all. Any unrecognized version refuses rather than guessing.
 */
@Component
public class DiscoveryProviderAdapterFactory {

    private final V1DiscoveryProviderAdapter v1Adapter;
    private final V2DiscoveryProviderAdapter v2Adapter;
    private final ConnectorInterfaceRepository connectorInterfaceRepository;

    public DiscoveryProviderAdapterFactory(V1DiscoveryProviderAdapter v1Adapter, V2DiscoveryProviderAdapter v2Adapter,
            ConnectorInterfaceRepository connectorInterfaceRepository) {
        this.v1Adapter = v1Adapter;
        this.v2Adapter = v2Adapter;
        this.connectorInterfaceRepository = connectorInterfaceRepository;
    }

    /**
     * Selects the adapter for the run. {@code null} tolerated on purpose: the caller may not have been able to load the
     * run (asynchronous start against a deleted uuid), and that failure belongs to the v1 flow's established handling,
     * not to routing.
     */
    public DiscoveryProviderAdapter forDiscovery(Discovery discovery) {
        if (discovery == null || discovery.getConnectorInterfaceUuid() == null) {
            return v1Adapter;
        }
        ConnectorInterfaceEntity iface = connectorInterfaceRepository
                .findById(discovery.getConnectorInterfaceUuid())
                .orElseThrow(() -> new UnsupportedDiscoveryVersionException(
                        "Discovery connector interface not found (discovery " + discovery.getUuid() + ")"));
        String version = iface.getVersion();
        if ("v2".equals(version)) {
            return v2Adapter;
        }
        throw new UnsupportedDiscoveryVersionException("Unsupported discovery connector interface version: " + version
                + " (discovery " + discovery.getUuid() + ")");
    }
}
