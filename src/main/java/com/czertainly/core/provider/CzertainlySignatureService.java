package com.czertainly.core.provider;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.core.provider.spi.CzertainlyCipherSpi;
import com.czertainly.core.provider.spi.CzertainlySignatureSpi;

import java.security.Provider;

public class CzertainlySignatureService extends Provider.Service {

    private CryptographicOperationsApiClient apiClient;

    public CzertainlySignatureService(Provider provider, String type, String algorithm, String className, CryptographicOperationsApiClient apiClient) {
        super(provider, type, algorithm, className, null, null);
        this.apiClient = apiClient;
    }


    @Override
    public Object newInstance(Object constructorParameter) {
        return new CzertainlySignatureSpi(this.apiClient);
    }
}
