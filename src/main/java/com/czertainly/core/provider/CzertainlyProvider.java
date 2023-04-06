package com.czertainly.core.provider;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.core.provider.spi.CzertainlyCipherSpi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.Security;

/**
 * JCA provider for cryptographic operations using Cryptographic Provider interface.
 */
public class CzertainlyProvider extends Provider {

    private static final Logger logger = LoggerFactory.getLogger(CzertainlyProvider.class);

    public static final String PROVIDER_NAME = "CzertainlyCryptographyProvider";

    public static CzertainlyProvider getInstance(String name, boolean registerProvider, CryptographicOperationsApiClient apiClient) {
        String instanceName = String.format("%s-%s", PROVIDER_NAME, name);
        CzertainlyProvider provider = new CzertainlyProvider(instanceName, apiClient);

        if (registerProvider) {
            if (Security.getProvider(provider.getName()) != null) {
                logger.info("Provider {} already registered.", provider.getName());
            } else {
                Security.addProvider(provider);
                logger.info("Provider {} registered.", provider.getName());
            }
        }

        return provider;
    }

    private CzertainlyProvider(String name, CryptographicOperationsApiClient apiClient) {
        super(name, 1.0, "CZERTAINLY Cryptography Provider");
        this.init(apiClient);
    }

    void init(CryptographicOperationsApiClient apiClient) {
        AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
            this.setupServices(apiClient);
            return null;
        });
    }

    void setupServices(CryptographicOperationsApiClient apiClient) {
        this.putService(new CzertainlyCipherService(this, "Cipher", "RSA", CzertainlyCipherSpi.class.getName(), apiClient));
    }

}