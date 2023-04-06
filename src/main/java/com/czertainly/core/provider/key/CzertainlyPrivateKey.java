package com.czertainly.core.provider.key;

import com.czertainly.api.model.core.connector.ConnectorDto;

import java.security.PrivateKey;

public class CzertainlyPrivateKey implements PrivateKey {
    private static final long serialVersionUID = 1L;
    private String keyUuid;

    private String tokenInstanceUuid;

    private ConnectorDto connectorDto;

    public CzertainlyPrivateKey(String tokenInstanceUuid, String keyUuid, ConnectorDto connectorDto) {
        this.keyUuid = keyUuid;
        this.connectorDto = connectorDto;
    }

    @Override
    public String getAlgorithm() {
        return null;
    }

    @Override
    public String getFormat() {
        return null;
    }

    @Override
    public byte[] getEncoded() {
        return new byte[0];
    }

    public String getKeyUuid() {
        return keyUuid;
    }

    public ConnectorDto getConnectorDto() {
        return connectorDto;
    }

    public String getTokenInstanceUuid() {
        return tokenInstanceUuid;
    }
}
