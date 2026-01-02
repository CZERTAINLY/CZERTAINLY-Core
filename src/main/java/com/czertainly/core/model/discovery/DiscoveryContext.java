package com.czertainly.core.model.discovery;

import com.czertainly.api.model.common.attribute.common.DataAttribute;
import com.czertainly.api.model.common.attribute.common.MetadataAttribute;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.discovery.DiscoveryStatus;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.DiscoveryHistory;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class DiscoveryContext {
    private final UUID loggedUserUuid;
    private final Connector connector;
    private final ConnectorDto connectorDto;
    private final DiscoveryHistory discoveryHistory;
    private final List<DataAttribute> dataAttributes;

    private String message;
    private DiscoveryStatus discoveryStatus;
    private DiscoveryStatus connectorDiscoveryStatus;
    private int certificatesDiscovered;
    private int connectorCertificatesDiscovered;

    private List<MetadataAttribute> metadata;

    public DiscoveryContext(UUID loggedUserUuid, Connector connector, DiscoveryHistory discoveryHistory, List<DataAttribute> dataAttributes) {
        this.loggedUserUuid = loggedUserUuid;
        this.connector = connector;
        this.connectorDto = connector != null ? connector.mapToDto() : null;
        this.discoveryHistory = discoveryHistory;
        this.dataAttributes = dataAttributes;
    }

    public void setDiscoveryFailed(String message) {
        this.discoveryStatus = DiscoveryStatus.FAILED;
        this.connectorDiscoveryStatus = DiscoveryStatus.FAILED;
        this.message = message;
    }
}
