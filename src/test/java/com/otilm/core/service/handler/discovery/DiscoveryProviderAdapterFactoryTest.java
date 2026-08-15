package com.otilm.core.service.handler.discovery;

import com.otilm.core.dao.entity.ConnectorInterfaceEntity;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.repository.ConnectorInterfaceRepository;
import com.otilm.core.exception.UnsupportedDiscoveryVersionException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the factory's routing table: no association routes to v1 — discovery's legacy default, the deliberate inverse of
 * the authority factory's NULL rule — a {@code "v2"} interface routes to the v2 adapter, and anything unrecognizable
 * refuses rather than guessing.
 */
class DiscoveryProviderAdapterFactoryTest {

    private V1DiscoveryProviderAdapter v1Adapter;
    private V2DiscoveryProviderAdapter v2Adapter;
    private ConnectorInterfaceRepository interfaceRepository;
    private DiscoveryProviderAdapterFactory factory;

    @BeforeEach
    void setUp() {
        v1Adapter = mock(V1DiscoveryProviderAdapter.class);
        v2Adapter = mock(V2DiscoveryProviderAdapter.class);
        interfaceRepository = mock(ConnectorInterfaceRepository.class);
        factory = new DiscoveryProviderAdapterFactory(v1Adapter, v2Adapter, interfaceRepository);
    }

    @Test
    void runWithoutInterfaceAssociationRoutesToV1() {
        Discovery run = new Discovery();

        assertThat(factory.forDiscovery(run)).isSameAs(v1Adapter);
    }

    @Test
    void unloadableRunRoutesToV1() {
        // The v1 flow owns the legacy failure handling for runs that cannot be loaded; routing must not
        // introduce a new failure mode in front of it.
        assertThat(factory.forDiscovery(null)).isSameAs(v1Adapter);
    }

    @Test
    void v2InterfaceRoutesToV2() {
        Discovery run = runWithInterfaceVersion("v2");

        assertThat(factory.forDiscovery(run)).isSameAs(v2Adapter);
    }

    @Test
    void unknownVersionRefuses() {
        Discovery run = runWithInterfaceVersion("v3");

        assertThatThrownBy(() -> factory.forDiscovery(run))
                .isInstanceOf(UnsupportedDiscoveryVersionException.class)
                .hasMessageContaining("v3");
    }

    @Test
    void missingInterfaceRowRefuses() {
        Discovery run = new Discovery();
        run.setConnectorInterfaceUuid(UUID.randomUUID());
        when(interfaceRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> factory.forDiscovery(run))
                .isInstanceOf(UnsupportedDiscoveryVersionException.class)
                .hasMessageContaining("not found");
    }

    private Discovery runWithInterfaceVersion(String version) {
        Discovery run = new Discovery();
        run.setConnectorInterfaceUuid(UUID.randomUUID());
        ConnectorInterfaceEntity iface = new ConnectorInterfaceEntity();
        iface.setVersion(version);
        when(interfaceRepository.findById(run.getConnectorInterfaceUuid())).thenReturn(Optional.of(iface));
        return run;
    }
}
