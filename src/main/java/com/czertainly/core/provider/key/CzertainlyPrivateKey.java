package com.czertainly.core.provider.key;

import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.core.connector.ConnectorDto;

import java.security.PrivateKey;
import java.util.List;

public class CzertainlyPrivateKey implements PrivateKey {
    private static final long serialVersionUID = 1L;
    private String keyUuid;

    private String tokenInstanceUuid;

    private ConnectorDto connectorDto;

    private String algorithm;

    private List<RequestAttributeDto> cipherAttributes;

    private List<RequestAttributeDto> signatureAttributes;

    public CzertainlyPrivateKey(String tokenInstanceUuid, String keyUuid, ConnectorDto connectorDto, String algorithm) {
        this.keyUuid = keyUuid;
        this.connectorDto = connectorDto;
        this.tokenInstanceUuid = tokenInstanceUuid;
        this.algorithm = algorithm;
    }

    @Override
    public String getAlgorithm() {
        return algorithm;
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

    public List<RequestAttributeDto> getCipherAttributes() {
        return cipherAttributes;
    }

    public void setCipherAttributes(List<RequestAttributeDto> cipherAttributes) {
        this.cipherAttributes = cipherAttributes;
    }

    public List<RequestAttributeDto> getSignatureAttributes() {
        return signatureAttributes;
    }

    public void setSignatureAttributes(List<RequestAttributeDto> signatureAttributes) {
        this.signatureAttributes = signatureAttributes;
    }
}
