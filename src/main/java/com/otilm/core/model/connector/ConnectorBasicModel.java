package com.otilm.core.model.connector;

import com.otilm.api.model.client.connector.v2.ConnectorVersion;
import com.otilm.api.model.core.connector.AuthType;
import java.util.List;
import java.util.UUID;

/** Connector identity and association references without expanded connector data. */
public interface ConnectorBasicModel {
    UUID uuid();

    String name();

    ConnectorVersion version();

    String url();

    AuthType authType();

    UUID proxyUuid();

    List<UUID> connectorInterfaceUuids();

    List<UUID> functionGroupUuids();
}
