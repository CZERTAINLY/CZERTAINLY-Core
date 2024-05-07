package com.czertainly.core.service.cmp;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.provider.CzertainlyProvider;
import com.czertainly.core.provider.key.CzertainlyPrivateKey;
import com.czertainly.core.provider.key.CzertainlyPublicKey;
import com.czertainly.core.service.CryptographicKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CertificateKeyService {

    private CryptographicOperationsApiClient cryptographicOperationsApiClient;
    @Autowired
    public void setCryptographicOperationsApiClient(CryptographicOperationsApiClient cryptographicOperationsApiClient) {
        this.cryptographicOperationsApiClient = cryptographicOperationsApiClient;
    }
    private CryptographicKeyService cryptographicKeyService;
    @Autowired
    public void setCryptographicKeyService(CryptographicKeyService cryptographicKeyService) {
        this.cryptographicKeyService = cryptographicKeyService;
    }

    public CzertainlyProvider getProvider (String cmpProfileName) {
        return CzertainlyProvider.getInstance(cmpProfileName,
                true, cryptographicOperationsApiClient);
    }

    public CzertainlyPrivateKey getPrivateKey(Certificate certificate) {
        CryptographicKey key = certificate.getKey();
        CryptographicKeyItem item = cryptographicKeyService.getKeyItemFromKey(key, KeyType.PRIVATE_KEY);
        TokenInstanceReference tokenInsReference = key.getTokenInstanceReference();
        return new CzertainlyPrivateKey(
                tokenInsReference.getTokenInstanceUuid(),
                item.getKeyReferenceUuid().toString(),
                tokenInsReference.getConnector().mapToDto(),
                item.getKeyAlgorithm().getLabel()
        );
    }

    public CzertainlyPublicKey getPublicKey(Certificate certificate) {
        CryptographicKey key = certificate.getKey();
        CryptographicKeyItem item = cryptographicKeyService.getKeyItemFromKey(key, KeyType.PUBLIC_KEY);
        TokenInstanceReference tokenInsReference = key.getTokenInstanceReference();
        return new CzertainlyPublicKey(
                tokenInsReference.getTokenInstanceUuid(),
                item.getKeyReferenceUuid().toString(),
                tokenInsReference.getConnector().mapToDto());
    }

}
