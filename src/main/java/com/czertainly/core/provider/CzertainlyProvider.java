package com.czertainly.core.provider;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.core.provider.spi.CzertainlyCipherSpi;
import com.czertainly.core.provider.spi.CzertainlySignatureSpi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Provider;
import java.security.Security;

/**
 * JCA provider for cryptographic operations using Cryptographic Provider interface.
 */
public class CzertainlyProvider extends Provider {

    public static final String PROVIDER_NAME = "CzertainlyCryptographyProvider";
    private static final Logger logger = LoggerFactory.getLogger(CzertainlyProvider.class);

    private CzertainlyProvider(String name, CryptographicOperationsApiClient apiClient) {
        super(name, 1.0, "CZERTAINLY Cryptography Provider");
        this.init(apiClient);
    }

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

    void init(CryptographicOperationsApiClient apiClient) {
        this.setupServices(apiClient);
    }

    void setupServices(CryptographicOperationsApiClient apiClient) {
        // Register RSA Cipher for encryption and decryption
        this.putService(new CzertainlyCipherService(this, "Cipher", "RSA", CzertainlyCipherSpi.class.getName(), apiClient));

        // Register Signature Ciphers

        putService(new CzertainlySignatureService(this, "Signature", "NonewithRSA", CzertainlySignatureSpi.class.getName(), apiClient));
        putService(new CzertainlySignatureService(this, "Signature", "SHA1withRSA", CzertainlySignatureSpi.class.getName(), apiClient));
        putService(new CzertainlySignatureService(this, "Signature", "SHA224withRSA", CzertainlySignatureSpi.class.getName(), apiClient));
        putService(new CzertainlySignatureService(this, "Signature", "SHA256withRSA", CzertainlySignatureSpi.class.getName(), apiClient));
        putService(new CzertainlySignatureService(this, "Signature", "SHA384withRSA", CzertainlySignatureSpi.class.getName(), apiClient));
        putService(new CzertainlySignatureService(this, "Signature", "SHA512withRSA", CzertainlySignatureSpi.class.getName(), apiClient));
        putService(new CzertainlySignatureService(this, "Signature", "NonewithRSA/PSS", CzertainlySignatureSpi.class.getName(), apiClient));
        putService(new CzertainlySignatureService(this, "Signature", "SHA1withRSA/PSS", CzertainlySignatureSpi.class.getName(), apiClient));
        putService(new CzertainlySignatureService(this, "Signature", "SHA224withRSA/PSS", CzertainlySignatureSpi.class.getName(), apiClient));
        putService(new CzertainlySignatureService(this, "Signature", "SHA256withRSA/PSS", CzertainlySignatureSpi.class.getName(), apiClient));
        putService(new CzertainlySignatureService(this, "Signature", "SHA384withRSA/PSS", CzertainlySignatureSpi.class.getName(), apiClient));
        putService(new CzertainlySignatureService(this, "Signature", "SHA512withRSA/PSS", CzertainlySignatureSpi.class.getName(), apiClient));
        putService(new CzertainlySignatureService(this, "Signature", "RMD160withRSA/PSS", CzertainlySignatureSpi.class.getName(), apiClient));
        putService(new CzertainlySignatureService(this, "Signature", "NonewithECDSA", CzertainlySignatureSpi.class.getName(), apiClient));
        putService(new CzertainlySignatureService(this, "Signature", "SHA1withECDSA", CzertainlySignatureSpi.class.getName(), apiClient));
        putService(new CzertainlySignatureService(this, "Signature", "SHA224withECDSA", CzertainlySignatureSpi.class.getName(), apiClient));
        putService(new CzertainlySignatureService(this, "Signature", "SHA256withECDSA", CzertainlySignatureSpi.class.getName(), apiClient));
        putService(new CzertainlySignatureService(this, "Signature", "SHA384withECDSA", CzertainlySignatureSpi.class.getName(), apiClient));
        putService(new CzertainlySignatureService(this, "Signature", "SHA512withECDSA", CzertainlySignatureSpi.class.getName(), apiClient));

    }

}