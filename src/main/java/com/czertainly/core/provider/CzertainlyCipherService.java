package com.czertainly.core.provider;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;

import java.security.Provider;
import java.util.List;
import java.util.Map;

public class CzertainlyCipherService extends Provider.Service {

    private CryptographicOperationsApiClient apiClient;

    public CzertainlyCipherService(Provider provider, String type, String algorithm, String className, CryptographicOperationsApiClient apiClient) {
        super(provider, type, algorithm, className, null, null);
        this.apiClient = apiClient;
    }


}
