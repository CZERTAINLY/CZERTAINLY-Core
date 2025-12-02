package com.czertainly.core.provider.key;

import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.api.model.core.connector.ConnectorDto;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;

public class CzertainlyPublicKey implements PublicKey {

    private final String keyUuid;

    private final String tokenInstanceUuid;

    private final ConnectorDto connectorDto;

    private byte[] data;

    public CzertainlyPublicKey(String tokenInstanceUuid, String keyUuid, ConnectorDto connectorDto) {
        this.keyUuid = keyUuid;
        this.connectorDto = connectorDto;
        this.tokenInstanceUuid = tokenInstanceUuid;
    }

    @Override
    public String getAlgorithm() {
        return "RSA";
    }

    @Override
    public String getFormat() {
        return "PKCS#8";
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

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
