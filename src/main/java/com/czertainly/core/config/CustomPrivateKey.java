package com.czertainly.core.config;

import java.security.PrivateKey;

public class CustomPrivateKey implements PrivateKey {
    private static final long serialVersionUID = 1L;
    private String keyUuid;

    public CustomPrivateKey(String keyUuid) {
        this.keyUuid = keyUuid;
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
}
